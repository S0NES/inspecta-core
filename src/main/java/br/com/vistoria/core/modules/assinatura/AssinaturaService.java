package br.com.vistoria.core.modules.assinatura;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.modules.laudo.LaudoService;
import br.com.vistoria.core.modules.notify.NotifyTemplates;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoInterveniente;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoService;
import br.com.vistoria.core.shared.outbox.OutboxService;
import br.com.vistoria.core.shared.webhook.WebhookRecebidoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssinaturaService {

    public interface SignatarioRepository extends JpaRepository<Signatario, UUID> {
        List<Signatario> findByDocumentoTipoAndDocumentoId(String tipo, UUID id);
        java.util.Optional<Signatario> findByExternalSignerId(String externalSignerId);
        List<Signatario> findByExternalDocumentId(String externalDocumentId);

        /**
         * Varredura do cron de lembrete: assinaturas ainda pendentes de um documento já enviado
         * à Autentique, criadas dentro da janela (link expira em 7d) e sem lembrete recente.
         */
        @Query(value = """
                SELECT * FROM signatarios
                WHERE status_assinatura = 'pendente'
                  AND external_document_id IS NOT NULL
                  AND signing_url IS NOT NULL
                  AND criado_em > :criadoDepoisDe
                  AND (lembrete_enviado_em IS NULL OR lembrete_enviado_em < :lembreteAntesDe)
                """, nativeQuery = true)
        List<Signatario> pendentesParaLembrete(@Param("criadoDepoisDe") Instant criadoDepoisDe,
                                               @Param("lembreteAntesDe") Instant lembreteAntesDe);

        @Query("""
                SELECT s FROM Signatario s
                WHERE (:temStatus = false OR s.statusAssinatura = :status)
                  AND (:temQ = false OR LOWER(s.nome) LIKE :q OR LOWER(s.email) LIKE :q
                       OR LOWER(COALESCE(s.externalDocumentId, '')) LIKE :q)
                ORDER BY s.criadoEm DESC
                """)
        org.springframework.data.domain.Page<Signatario> painel(
                @Param("temStatus") boolean temStatus,
                @Param("status") String status,
                @Param("temQ") boolean temQ,
                @Param("q") String q,
                org.springframework.data.domain.Pageable pageable);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Signatario> listarPainel(
            String status, String q, org.springframework.data.domain.Pageable pageable) {
        boolean temStatus = status != null && !status.isBlank();
        String termo = q == null ? "" : q.trim().toLowerCase();
        boolean temQ = !termo.isBlank();
        return signatarios.painel(temStatus, temStatus ? status : "",
                temQ, temQ ? "%" + termo + "%" : "", pageable);
    }

    private final SignatarioRepository signatarios;
    private final WebhookRecebidoService webhooks;
    private final LaudoService laudoService;
    private final SolicitacaoService solicitacaoService;
    private final OutboxService outbox;
    private final ObjectMapper mapper;

    /**
     * lets the inspecta-messenger webhook consumer short-circuit BEFORE the Arquivo write on a
     * replayed {@code document.finished}, instead of relying on the unique r2_key constraint.
     */
    @Transactional(readOnly = true)
    public boolean documentoJaConcluido(String externalDocumentId) {
        List<Signatario> partes = signatarios.findByExternalDocumentId(externalDocumentId);
        if (partes.isEmpty()) return false;
        Signatario primeira = partes.get(0);
        if (!"laudo".equals(primeira.getDocumentoTipo())) return false;
        return laudoService.get(primeira.getDocumentoId()).getStatus() == br.com.vistoria.core.modules.laudo.Laudo.Status.assinado;
    }

    /**
     * Os signatários vêm SEMPRE de {@code solicitacao_intervenientes} (via
     * laudo → solicitacao_servico → solicitacao). A lista recebida no corpo é ignorada.
     *
     * @param corpoIgnorado mantido só para não quebrar a assinatura do controller.
     * @throws IllegalArgumentException {@code tipo_documento_invalido} se {@code tipo != "laudo"};
     *         {@code sem_intervenientes} se a solicitação não tiver nenhuma pessoa gravada.
     */
    @Transactional
    public void solicitarAssinatura(String tipo, UUID documentoId, List<Signatario> corpoIgnorado) {
        if (!"laudo".equals(tipo))
            throw new IllegalArgumentException("tipo_documento_invalido");

        UUID servicoId = laudoService.get(documentoId).getSolicitacaoServicoId();
        List<SolicitacaoInterveniente> pessoas = solicitacaoService.intervenientesDoServico(servicoId);
        if (pessoas.isEmpty())
            throw new IllegalArgumentException("sem_intervenientes");

        List<Signatario> lista = pessoas.stream().map(p -> {
            Signatario s = new Signatario();
            s.setDocumentoTipo(tipo);
            s.setDocumentoId(documentoId);
            s.setNome(p.getNome());
            s.setEmail(p.getEmail());
            s.setPapel(p.getPapelSlug());
            return s;
        }).toList();
        signatarios.saveAll(lista);
        outbox.publish(EventTypes.SIGNATURE_REQUESTED, Map.of(
                "eventId", "sign-" + tipo + "-" + documentoId,
                "documentoTipo", tipo,
                "documentoId", documentoId));
    }

    /**
     * Webhook da Autentique (borda: {@code inspecta-messenger}, HMAC {@code x-autentique-signature} já
     * validado fail-closed antes de chegar aqui). Diferente da Assinafy (que não assinava os
     * webhooks), a Autentique assina, então o payload é confiável e o status é aplicado direto —
     * mesmo modelo assíncrono do broker de pagamento:
     *
     * <p>dedup por {@code event.id} ({@link WebhookRecebidoService#receber}) → publica
     * {@link EventTypes#SIGNATURE_WEBHOOK} via outbox (relay drena) → o {@code inspecta-messenger}
     * ({@code SignatureWebhookConsumer}) faz o parse e aplica ({@link #aplicarEventoAssinatura} /
     * {@link #aplicarDocumentoAssinado}).</p>
     */
    /**
     * Tipos de webhook da Autentique com efeito de domínio. A Autentique dispara ~4 eventos por
     * assinatura ({@code signature.viewed}, {@code signature.updated}×2, {@code signature.accepted})
     * — só persistimos/publicamos os que movem o estado, pra não encher {@code webhooks_recebidos}
     * e a fila de ruído. {@code 200} é devolvido pros demais na mesma; sem persistir.
     */
    private static final java.util.Set<String> EVENTOS_RELEVANTES = java.util.Set.of(
            "document.finished", "signature.accepted", "signature.rejected");

    @Transactional
    public void receberWebhook(String payload, Map<String, String> headers) {
        try {
            JsonNode json = mapper.readTree(payload);
            JsonNode event = json.path("event");
            String eventId = event.path("id").asText(null);
            if (eventId == null || eventId.isBlank()) {
                log.warn("Webhook Autentique sem event.id - ignorado");
                return;
            }
            String tipo = event.path("type").asText("");
            if (!EVENTOS_RELEVANTES.contains(tipo)) {
                log.debug("Webhook Autentique {} ({}) sem efeito de dominio - ignorado", tipo, eventId);
                return;
            }
            webhooks.receber("autentique", eventId, payload).ifPresent(w ->
                    outbox.publish(EventTypes.SIGNATURE_WEBHOOK, Map.of(
                            "eventId", eventId,
                            "payload", payload)));
        } catch (Exception e) {
            // Pode ser payload malformado OU falha de infra (banco/outbox indisponivel). Nos dois
            // casos o webhook nao foi persistido/publicado — investigar antes de assumir "lixo do provedor".
            log.error("Falha ao processar webhook Autentique (payload invalido ou erro de infra)", e);
        }
    }

    /**
     * Aplica um evento de webhook da Autentique (chamado pelo {@code inspecta-messenger} após o parse).
     * {@code document.finished} é tratado à parte (baixa do PDF assinado) — ver {@link #aplicarDocumentoAssinado}.
     */
    @Transactional
    public void aplicarEventoAssinatura(String eventId, String tipo, String externalDocumentId, String externalSignerId) {
        switch (tipo) {
            case "signature.accepted" -> aplicarStatus(externalSignerId, "assinado");
            case "signature.rejected" -> aplicarStatus(externalSignerId, "recusado");
            default -> log.info("[autentique] evento {} ({}) sem ação de domínio - documento={}",
                    tipo, eventId, externalDocumentId);
        }
    }

    @Transactional
    public void aplicarStatus(String externalSignerId, String status) {
        signatarios.findByExternalSignerId(externalSignerId).ifPresent(s -> {
            s.setStatusAssinatura(status);
            if ("assinado".equals(status)) {
                if (s.getAssinadoEm() == null) s.setAssinadoEm(Instant.now());
                verificarConclusao(s);
            }
        });
    }

    /** Signatarios de um documento (usado pelo preparo no inspecta-worker para montar a lista de signers). */
    @Transactional(readOnly = true)
    public List<Signatario> signatariosDoDocumento(String documentoTipo, UUID documentoId) {
        return signatarios.findByDocumentoTipoAndDocumentoId(documentoTipo, documentoId);
    }

    /**
     * Lista, para o cron de lembrete (inspecta-worker), quem ainda não assinou dentro da janela do
     * link e sem lembrete recente. {@code janela} = validade do link (default 7d);
     * {@code intervalo} = espaçamento mínimo entre lembretes.
     */
    @Transactional(readOnly = true)
    public List<Signatario> pendentesParaLembrete(Duration janela, Duration intervalo) {
        Instant agora = Instant.now();
        return signatarios.pendentesParaLembrete(agora.minus(janela), agora.minus(intervalo));
    }

    /** Marca que o e-mail de lembrete foi enviado agora para este signatario. */
    @Transactional
    public void registrarLembreteEnviado(UUID signatarioId) {
        signatarios.findById(signatarioId).ifPresent(s -> s.setLembreteEnviadoEm(Instant.now()));
    }

    /** Quem vai assinar um laudo — as pessoas de {@code solicitacao_intervenientes} da solicitação. */
    @Transactional(readOnly = true)
    public List<SolicitacaoInterveniente> assinantesDoLaudo(UUID laudoId) {
        UUID servicoId = laudoService.get(laudoId).getSolicitacaoServicoId();
        return solicitacaoService.intervenientesDoServico(servicoId);
    }

    /**
     * Vincula ao {@code Signatario} os ids da Autentique (assinante + documento) e o
     * {@code signing_url} capturado na criação do documento, e dispara o e-mail com o link de
     * assinatura para aquele interveniente (template utilitário {@code assinatura_link}).
     * Chamado pelo {@code inspecta-messenger} ({@code SignatureCreateConsumer}) após {@code SignatureBroker.criarDocumento()}.
     */
    @Transactional
    public void vincularDocumento(UUID signatarioId, String externalSignerId, String externalDocumentId, String signingUrl) {
        signatarios.findById(signatarioId).ifPresent(s -> {
            s.setExternalSignerId(externalSignerId);
            s.setExternalDocumentId(externalDocumentId);
            s.setSigningUrl(signingUrl);
            if (signingUrl != null && !signingUrl.isBlank()) {
                outbox.publish("notify.requested.assinatura-link", Map.of(
                        "eventId", "sign-link-" + signatarioId,
                        "canal", "email",
                        "tipoComunicacao", "assinatura",
                        "templateId", NotifyTemplates.ASSINATURA_LINK,
                        "destinatario", s.getEmail(),
                        "variaveis", Map.of("nome", s.getNome(), "signingUrl", signingUrl)));
            } else {
                log.warn("[autentique] signatario {} sem signing_url - e-mail de link não enviado", signatarioId);
            }
        });
    }

    /**
     * Webhook document.finished (todos assinaram): marca todos os signatarios do documento,
     * troca o PDF pelo assinado (ja registrado como Arquivo por quem chamou) e notifica.
     */
    @Transactional
    public void aplicarDocumentoAssinado(String externalDocumentId, UUID pdfAssinadoArquivoId) {
        List<Signatario> partes = signatarios.findByExternalDocumentId(externalDocumentId);
        if (partes.isEmpty()) {
            log.warn("Webhook Autentique document.finished sem signatarios locais externalDocumentId={}", externalDocumentId);
            return;
        }
        Signatario primeira = partes.get(0);
        if ("laudo".equals(primeira.getDocumentoTipo())
                && laudoService.get(primeira.getDocumentoId()).getStatus() == br.com.vistoria.core.modules.laudo.Laudo.Status.assinado) {
            log.info("[autentique] replay ignorado - laudo {} ja assinado", primeira.getDocumentoId());
            return;
        }
        partes.forEach(s -> {
            s.setStatusAssinatura("assinado");
            if (s.getAssinadoEm() == null) s.setAssinadoEm(Instant.now());
        });
        if ("laudo".equals(primeira.getDocumentoTipo()) && pdfAssinadoArquivoId != null) {
            laudoService.marcarPdf(primeira.getDocumentoId(), pdfAssinadoArquivoId);
        }
        concluirDocumento(primeira.getDocumentoTipo(), primeira.getDocumentoId(), partes);
    }

    private void verificarConclusao(Signatario s) {
        List<Signatario> partes = signatarios.findByDocumentoTipoAndDocumentoId(s.getDocumentoTipo(), s.getDocumentoId());
        boolean todos = partes.stream().allMatch(x -> "assinado".equals(x.getStatusAssinatura()));
        if (todos) concluirDocumento(s.getDocumentoTipo(), s.getDocumentoId(), partes);
    }

    private void concluirDocumento(String documentoTipo, UUID documentoId, List<Signatario> partes) {
        if (!"laudo".equals(documentoTipo)) return;
        laudoService.marcarAssinado(documentoId);
        for (Signatario parte : partes) {
            outbox.publish("notify.requested.laudo-assinado", Map.of(
                    "eventId", "laudo-signed-" + documentoId + "-" + parte.getId(),
                    "canal", "email", "templateId", NotifyTemplates.LAUDO_ASSINADO,
                    "tipoComunicacao", "assinatura_laudo",
                    "destinatario", parte.getEmail(),
                    "variaveis", Map.of("laudoId", documentoId, "nome", parte.getNome())));
        }
    }
}
