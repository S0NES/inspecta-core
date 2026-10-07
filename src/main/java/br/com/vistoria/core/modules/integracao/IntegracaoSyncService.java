package br.com.vistoria.core.modules.integracao;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntegracaoSyncService {

    private final IntegracaoSyncRepository repository;
    private final OutboxService outbox;

    @Transactional
    public IntegracaoSync enfileirar(String recursoTipo, UUID recursoId) {
        return enfileirarInterno(recursoTipo, recursoId);
    }

    /**
     * Igual a {@link #enfileirar}, mas em transação própria ({@code REQUIRES_NEW}). Para o
     * espelhamento no inspecta-worker reenfileirar uma <em>dependência</em> do DAG a partir de um
     * {@code espelhar()} que, logo em seguida, lança e dá rollback: com {@code REQUIRES_NEW} a
     * linha da dependência (e seu outbox) commitam por fora da transação desfeita — senão a
     * dependência nunca entra na fila e o recurso filho fica preso em {@code erro}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IntegracaoSync enfileirarIsolado(String recursoTipo, UUID recursoId) {
        return enfileirarInterno(recursoTipo, recursoId);
    }

    private IntegracaoSync enfileirarInterno(String recursoTipo, UUID recursoId) {
        IntegracaoSync row = repository.findBySistemaAndRecursoTipoAndRecursoId("pleno", recursoTipo, recursoId)
                .orElseGet(() -> {
                    IntegracaoSync n = new IntegracaoSync();
                    n.setRecursoTipo(recursoTipo);
                    n.setRecursoId(recursoId);
                    return n;
                });
        row.setStatus(IntegracaoSync.Status.pendente.name());
        row.setErro(null);
        repository.save(row);
        publicar(row);
        return row;
    }

    @Transactional
    public void aplicarResultado(UUID id, String status, String externalId, String erro) {
        IntegracaoSync row = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("integracao_sync " + id));
        row.setStatus(status);
        if (externalId != null) row.setExternalId(externalId);
        row.setErro(erro);
        row.setUltimoSync(Instant.now());
    }

    @Transactional
    public void drenarPendentes() {
        repository.findTop50ByStatusInOrderByIdAsc(List.of(
                IntegracaoSync.Status.pendente.name(), IntegracaoSync.Status.erro.name())
        ).forEach(this::publicar);
    }

    /**
     * Cadastro externo: vincula um recurso local a um registro que JA existe
     * na Pleno, pelo ID informado pelo usuario. Grava external_id + status=sincronizado
     * + origem=manual e NAO publica evento de espelhamento. Idempotente: revincular com
     * o mesmo ID e efetivamente no-op; ID diferente atualiza a linha.
     */
    @Transactional
    public IntegracaoSync vincularExterno(String recursoTipo, UUID recursoId, String externalId) {
        IntegracaoSync row = repository.findBySistemaAndRecursoTipoAndRecursoId("pleno", recursoTipo, recursoId)
                .orElseGet(() -> {
                    IntegracaoSync n = new IntegracaoSync();
                    n.setRecursoTipo(recursoTipo);
                    n.setRecursoId(recursoId);
                    return n;
                });
        row.setExternalId(externalId);
        row.setStatus(IntegracaoSync.Status.sincronizado.name());
        row.setOrigem(IntegracaoSync.Origem.manual.name());
        row.setErro(null);
        row.setUltimoSync(Instant.now());
        return repository.save(row);
    }

    /** Estado do espelho Pleno de um recurso, para a UI ("espelho Pleno pendente/falhou"). */
    @Transactional(readOnly = true)
    public java.util.Optional<IntegracaoSync> buscar(String recursoTipo, UUID recursoId) {
        return repository.findBySistemaAndRecursoTipoAndRecursoId("pleno", recursoTipo, recursoId);
    }

    /** Painel de integração Pleno (admin): lista paginada, filtro por status e busca por nome. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<IntegracaoSync> listar(
            String status, org.springframework.data.domain.Pageable p) {
        return listar(status, null, p);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<IntegracaoSync> listar(
            String status, String q, org.springframework.data.domain.Pageable p) {
        boolean temStatus = status != null && !status.isBlank();
        String termo = q == null ? "" : q.trim().toLowerCase();
        boolean temQ = !termo.isBlank();
        return repository.buscarPainel("pleno", temStatus, temStatus ? status : "",
                temQ, temQ ? "%" + termo + "%" : "", p);
    }

    /**
     * Re-tenta o espelhamento de uma linha (botão "re-tentar" do painel):
     * <ul>
     *   <li>{@code erro} → volta para {@code pendente}, limpa o erro e re-publica;</li>
     *   <li>{@code sincronizado} → mantém o status e re-publica só um {@code sync_status}
     *       (refresh, nunca re-cria);</li>
     *   <li>{@code pendente} → já está na fila (backoff/cron); NÃO re-publica, para um
     *       clique repetido no botão não empilhar N eventos {@code espelhar} — o que,
     *       numa solicitação, duplicaria o cadastro na Pleno.</li>
     * </ul>
     */
    @Transactional
    public IntegracaoSync retentar(UUID id) {
        IntegracaoSync row = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("integracao_sync " + id));
        if (IntegracaoSync.Status.pendente.name().equals(row.getStatus())) {
            return row; // já enfileirada — não re-publica
        }
        if (!IntegracaoSync.Status.sincronizado.name().equals(row.getStatus())) {
            row.setStatus(IntegracaoSync.Status.pendente.name());
        }
        row.setErro(null);
        repository.save(row);
        publicar(row);
        return row;
    }

    private void publicar(IntegracaoSync row) {
        // Linha sincronizada: sempre um refresh de status, nunca um cadastro novo —
        // mesmo que o external_id ainda não esteja gravado nesta transação.
        boolean sincronizada = IntegracaoSync.Status.sincronizado.name().equals(row.getStatus());
        String acao = (sincronizada || row.getExternalId() != null) ? "sync_status" : "espelhar";
        outbox.publish(EventTypes.PLENO_SYNC_REQUESTED, Map.of(
                "eventId", UUID.randomUUID().toString(),
                "integracaoSyncId", row.getId(),
                "recursoTipo", row.getRecursoTipo(),
                "recursoId", row.getRecursoId(),
                "acao", acao));
    }
}
