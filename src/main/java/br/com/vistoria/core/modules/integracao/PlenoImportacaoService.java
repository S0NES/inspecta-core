package br.com.vistoria.core.modules.integracao;

import br.com.vistoria.core.modules.integracao.PlenoImportacaoRepositories.PlenoImportacaoCheckpointRepository;
import br.com.vistoria.core.modules.integracao.PlenoImportacaoRepositories.PlenoImportacaoVistoriaRepository;
import br.com.vistoria.core.modules.integracao.PlenoImportacaoVistoria.Status;
import br.com.vistoria.core.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * V3: camada de dados do acompanhamento de importação Pleno (spec:
 * PLENO_INTEGRATION_PROMPT.md §4). Estilo {@link IntegracaoSyncService}: métodos
 * pequenos, um por transição de estado. NÃO chama a API Pleno — isso é do inspecta-worker.
 */
@Service
@RequiredArgsConstructor
public class PlenoImportacaoService {

    /** Status ainda "vivos" para o poller (os demais são terminais: enviada / cancelada / erro). */
    private static final List<String> EM_ACOMPANHAMENTO = List.of(
            Status.SOLICITADA.name(), Status.EM_ANDAMENTO.name(),
            Status.FINALIZADA_SEM_PDF.name(), Status.FINALIZADA_COM_PDF.name(),
            Status.PDF_BAIXADO.name());

    private final PlenoImportacaoVistoriaRepository repository;
    private final PlenoImportacaoCheckpointRepository checkpoints;

    /** Linhas cujo acompanhamento continua e cuja próxima verificação já venceu. */
    @Transactional(readOnly = true)
    public List<PlenoImportacaoVistoria> elegiveis() {
        return repository.findByStatusImportacaoInAndProximaVerificacaoEmLessThanEqual(
                EM_ACOMPANHAMENTO, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<PlenoImportacaoVistoria> listarPorStatus(Status status) {
        return repository.findByStatusImportacao(status.name());
    }

    @Transactional(readOnly = true)
    public Optional<PlenoImportacaoVistoria> buscar(UUID solicitacaoServicoId) {
        return repository.findBySolicitacaoServicoId(solicitacaoServicoId);
    }

    /**
     * Upsert de progresso do poller: cria a linha na primeira vez que a solicitação-serviço
     * aparece, senão atualiza. {@code tentativas_verificacao} conta verificações <em>seguidas
     * sem progredir</em>: zera quando o status muda, incrementa só se permanece o mesmo.
     */
    @Transactional
    public PlenoImportacaoVistoria registrarProgresso(UUID solicitacaoServicoId, Integer staSolCodigo,
                                                      Status novoStatus, Instant proximaVerificacaoEm) {
        PlenoImportacaoVistoria row = repository.findBySolicitacaoServicoId(solicitacaoServicoId)
                .orElseGet(() -> {
                    PlenoImportacaoVistoria n = new PlenoImportacaoVistoria();
                    n.setSolicitacaoServicoId(solicitacaoServicoId);
                    return n;
                });
        boolean mudou = !novoStatus.name().equals(row.getStatusImportacao());
        row.setStaSolCodigoAtual(staSolCodigo);
        row.setStatusImportacao(novoStatus.name());
        row.setTentativasVerificacao(mudou ? 0 : row.getTentativasVerificacao() + 1);
        Instant agora = Instant.now();
        row.setUltimaVerificacaoEm(agora);
        if (proximaVerificacaoEm != null) row.setProximaVerificacaoEm(proximaVerificacaoEm);
        row.setAtualizadoEm(agora);
        return repository.save(row);
    }

    /**
     * Vistoria concluída na Pleno com link de PDF disponível. Zera o contador: chegar
     * aqui é progresso (o orçamento de retry da ingestão começa do zero).
     */
    @Transactional
    public PlenoImportacaoVistoria marcarPdfDisponivel(UUID solicitacaoServicoId, String linkPdfExterno) {
        PlenoImportacaoVistoria row = get(solicitacaoServicoId);
        row.setLinkPdfExterno(linkPdfExterno);
        row.setStatusImportacao(Status.FINALIZADA_COM_PDF.name());
        row.setTentativasVerificacao(0);
        row.setAtualizadoEm(Instant.now());
        return row;
    }

    /**
     * Incrementa {@code tentativas_verificacao} sem mudar o status — falha de ingestão /
     * processamento que deve esgotar o orçamento e escalar para {@code ERRO}.
     */
    @Transactional
    public PlenoImportacaoVistoria incrementarTentativa(UUID solicitacaoServicoId,
                                                        Instant proximaVerificacaoEm) {
        PlenoImportacaoVistoria row = get(solicitacaoServicoId);
        row.setTentativasVerificacao(row.getTentativasVerificacao() + 1);
        Instant agora = Instant.now();
        row.setUltimaVerificacaoEm(agora);
        if (proximaVerificacaoEm != null) row.setProximaVerificacaoEm(proximaVerificacaoEm);
        row.setAtualizadoEm(agora);
        return row;
    }

    /** PDF baixado, gravado em {@code arquivos} e vinculado ao laudo. */
    @Transactional
    public PlenoImportacaoVistoria marcarPdfBaixado(UUID solicitacaoServicoId, UUID pdfArquivoId,
                                                    String pdfSha256, UUID laudoId) {
        PlenoImportacaoVistoria row = get(solicitacaoServicoId);
        row.setPdfArquivoId(pdfArquivoId);
        row.setPdfSha256(pdfSha256);
        row.setLaudoId(laudoId);
        row.setStatusImportacao(Status.PDF_BAIXADO.name());
        row.setAtualizadoEm(Instant.now());
        return row;
    }

    /** {@code jobs.signature.send} publicado para o laudo importado. */
    @Transactional
    public PlenoImportacaoVistoria marcarEnviadoParaAssinatura(UUID solicitacaoServicoId) {
        PlenoImportacaoVistoria row = get(solicitacaoServicoId);
        Instant agora = Instant.now();
        row.setEnviadoParaAssinaturaEm(agora);
        row.setStatusImportacao(Status.ENVIADA_PARA_ASSINATURA.name());
        row.setAtualizadoEm(agora);
        return row;
    }

    @Transactional
    public PlenoImportacaoVistoria marcarErro(UUID solicitacaoServicoId, String mensagem) {
        PlenoImportacaoVistoria row = get(solicitacaoServicoId);
        row.setErroMensagem(mensagem);
        row.setStatusImportacao(Status.ERRO.name());
        row.setAtualizadoEm(Instant.now());
        return row;
    }


    @Transactional(readOnly = true)
    public Optional<Instant> lerCheckpoint() {
        return checkpoints.findById((short) 1).map(PlenoImportacaoCheckpoint::getUltimoCheckpoint);
    }

    @Transactional
    public void salvarCheckpoint(Instant ultimoCheckpoint) {
        PlenoImportacaoCheckpoint cp = checkpoints.findById((short) 1)
                .orElseGet(PlenoImportacaoCheckpoint::new);
        cp.setUltimoCheckpoint(ultimoCheckpoint);
        checkpoints.save(cp);
    }

    private PlenoImportacaoVistoria get(UUID solicitacaoServicoId) {
        return repository.findBySolicitacaoServicoId(solicitacaoServicoId)
                .orElseThrow(() -> new NotFoundException(
                        "pleno_importacao_vistoria para solicitacao_servico " + solicitacaoServicoId));
    }
}
