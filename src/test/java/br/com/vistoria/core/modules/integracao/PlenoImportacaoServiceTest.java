package br.com.vistoria.core.modules.integracao;

import br.com.vistoria.core.modules.integracao.PlenoImportacaoRepositories.PlenoImportacaoCheckpointRepository;
import br.com.vistoria.core.modules.integracao.PlenoImportacaoRepositories.PlenoImportacaoVistoriaRepository;
import br.com.vistoria.core.modules.integracao.PlenoImportacaoVistoria.Status;
import br.com.vistoria.core.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlenoImportacaoService")
class PlenoImportacaoServiceTest {

    @Mock private PlenoImportacaoVistoriaRepository repository;
    @Mock private PlenoImportacaoCheckpointRepository checkpoints;

    private PlenoImportacaoService service;

    private static final UUID SS = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @BeforeEach
    void setUp() {
        service = new PlenoImportacaoService(repository, checkpoints);
    }

    private void stubSaveEcho() {
        when(repository.save(any(PlenoImportacaoVistoria.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("registrarProgresso cria a linha na primeira vez (upsert) e zera tentativas (status mudou)")
    void registrarProgresso_criaQuandoAusente() {
        when(repository.findBySolicitacaoServicoId(SS)).thenReturn(Optional.empty());
        stubSaveEcho();

        Instant proxima = Instant.now().plusSeconds(3600);
        PlenoImportacaoVistoria row = service.registrarProgresso(SS, 3, Status.EM_ANDAMENTO, proxima);

        assertThat(row.getSolicitacaoServicoId()).isEqualTo(SS);
        assertThat(row.getStaSolCodigoAtual()).isEqualTo(3);
        assertThat(row.getStatusImportacao()).isEqualTo("EM_ANDAMENTO");
        assertThat(row.getTentativasVerificacao()).isZero();
        assertThat(row.getProximaVerificacaoEm()).isEqualTo(proxima);
        assertThat(row.getUltimaVerificacaoEm()).isNotNull();
    }

    @Test
    @DisplayName("registrarProgresso no mesmo status incrementa tentativas (sem progredir)")
    void registrarProgresso_mesmoStatus_incrementa() {
        PlenoImportacaoVistoria existente = new PlenoImportacaoVistoria();
        existente.setSolicitacaoServicoId(SS);
        existente.setTentativasVerificacao(2);
        existente.setStatusImportacao(Status.EM_ANDAMENTO.name());
        when(repository.findBySolicitacaoServicoId(SS)).thenReturn(Optional.of(existente));
        stubSaveEcho();

        PlenoImportacaoVistoria row = service.registrarProgresso(SS, 3, Status.EM_ANDAMENTO, null);

        assertThat(row).isSameAs(existente);
        assertThat(row.getTentativasVerificacao()).isEqualTo(3);
        assertThat(row.getStatusImportacao()).isEqualTo("EM_ANDAMENTO");
    }

    @Test
    @DisplayName("registrarProgresso zera tentativas quando o status muda (EM_ANDAMENTO → FINALIZADA_SEM_PDF)")
    void registrarProgresso_statusMudou_zeraTentativas() {
        PlenoImportacaoVistoria existente = new PlenoImportacaoVistoria();
        existente.setSolicitacaoServicoId(SS);
        existente.setTentativasVerificacao(8);
        existente.setStatusImportacao(Status.EM_ANDAMENTO.name());
        when(repository.findBySolicitacaoServicoId(SS)).thenReturn(Optional.of(existente));
        stubSaveEcho();

        PlenoImportacaoVistoria row = service.registrarProgresso(SS, 4, Status.FINALIZADA_SEM_PDF, null);

        assertThat(row.getTentativasVerificacao()).isZero();
        assertThat(row.getStatusImportacao()).isEqualTo("FINALIZADA_SEM_PDF");
    }

    @Test
    @DisplayName("transições de status: pdf disponível → baixado → enviado para assinatura")
    void transicoesDeStatus() {
        PlenoImportacaoVistoria row = new PlenoImportacaoVistoria();
        row.setSolicitacaoServicoId(SS);
        when(repository.findBySolicitacaoServicoId(SS)).thenReturn(Optional.of(row));

        service.marcarPdfDisponivel(SS, "https://pleno/pdf/1");
        assertThat(row.getStatusImportacao()).isEqualTo("FINALIZADA_COM_PDF");
        assertThat(row.getLinkPdfExterno()).isEqualTo("https://pleno/pdf/1");
        assertThat(row.getTentativasVerificacao()).isZero();

        UUID arq = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        UUID laudo = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        service.marcarPdfBaixado(SS, arq, "deadbeef", laudo);
        assertThat(row.getStatusImportacao()).isEqualTo("PDF_BAIXADO");
        assertThat(row.getPdfArquivoId()).isEqualTo(arq);
        assertThat(row.getPdfSha256()).isEqualTo("deadbeef");
        assertThat(row.getLaudoId()).isEqualTo(laudo);

        service.marcarEnviadoParaAssinatura(SS);
        assertThat(row.getStatusImportacao()).isEqualTo("ENVIADA_PARA_ASSINATURA");
        assertThat(row.getEnviadoParaAssinaturaEm()).isNotNull();
    }

    @Test
    @DisplayName("marcarErro seta ERRO + mensagem")
    void marcarErro() {
        PlenoImportacaoVistoria row = new PlenoImportacaoVistoria();
        row.setSolicitacaoServicoId(SS);
        when(repository.findBySolicitacaoServicoId(SS)).thenReturn(Optional.of(row));

        service.marcarErro(SS, "timeout no download");

        assertThat(row.getStatusImportacao()).isEqualTo("ERRO");
        assertThat(row.getErroMensagem()).isEqualTo("timeout no download");
    }

    @Test
    @DisplayName("incrementarTentativa soma 1 e mantém o status")
    void incrementarTentativa() {
        PlenoImportacaoVistoria row = new PlenoImportacaoVistoria();
        row.setSolicitacaoServicoId(SS);
        row.setStatusImportacao(Status.FINALIZADA_COM_PDF.name());
        row.setTentativasVerificacao(3);
        when(repository.findBySolicitacaoServicoId(SS)).thenReturn(Optional.of(row));

        Instant proxima = Instant.now().plusSeconds(900);
        PlenoImportacaoVistoria out = service.incrementarTentativa(SS, proxima);

        assertThat(out.getTentativasVerificacao()).isEqualTo(4);
        assertThat(out.getStatusImportacao()).isEqualTo("FINALIZADA_COM_PDF");
        assertThat(out.getProximaVerificacaoEm()).isEqualTo(proxima);
    }

    @Test
    @DisplayName("marcar* em solicitacao_servico sem linha → NotFoundException")
    void marcar_semLinha_lanca() {
        when(repository.findBySolicitacaoServicoId(SS)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.marcarPdfDisponivel(SS, "x"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("elegiveis delega ao repositório com os status em acompanhamento")
    void elegiveis_delega() {
        PlenoImportacaoVistoria row = new PlenoImportacaoVistoria();
        when(repository.findByStatusImportacaoInAndProximaVerificacaoEmLessThanEqual(anyList(), any()))
                .thenReturn(List.of(row));

        assertThat(service.elegiveis()).containsExactly(row);
    }

    @Test
    @DisplayName("listarPorStatus delega")
    void listarPorStatus_delega() {
        when(repository.findByStatusImportacao("CANCELADA")).thenReturn(List.of());
        assertThat(service.listarPorStatus(Status.CANCELADA)).isEmpty();
    }

    @Test
    @DisplayName("buscar delega")
    void buscar_delega() {
        PlenoImportacaoVistoria row = new PlenoImportacaoVistoria();
        when(repository.findBySolicitacaoServicoId(SS)).thenReturn(Optional.of(row));
        assertThat(service.buscar(SS)).contains(row);
    }

    @Test
    @DisplayName("checkpoint: lê e grava a linha única id=1")
    void checkpoint_readWrite() {
        when(checkpoints.findById((short) 1)).thenReturn(Optional.empty());
        assertThat(service.lerCheckpoint()).isEmpty();

        when(checkpoints.save(any(PlenoImportacaoCheckpoint.class))).thenAnswer(i -> i.getArgument(0));
        Instant t = Instant.parse("2026-09-01T00:00:00Z");
        service.salvarCheckpoint(t);

        org.mockito.ArgumentCaptor<PlenoImportacaoCheckpoint> cap =
                org.mockito.ArgumentCaptor.forClass(PlenoImportacaoCheckpoint.class);
        verify(checkpoints).save(cap.capture());
        assertThat(cap.getValue().getUltimoCheckpoint()).isEqualTo(t);
    }

    @Test
    @DisplayName("checkpoint: atualiza a linha existente id=1")
    void checkpoint_updateExistente() {
        PlenoImportacaoCheckpoint cp = new PlenoImportacaoCheckpoint();
        cp.setUltimoCheckpoint(Instant.parse("2026-08-01T00:00:00Z"));
        when(checkpoints.findById((short) 1)).thenReturn(Optional.of(cp));
        when(checkpoints.save(any(PlenoImportacaoCheckpoint.class))).thenAnswer(i -> i.getArgument(0));

        Instant t = Instant.parse("2026-09-10T00:00:00Z");
        service.salvarCheckpoint(t);

        assertThat(cp.getUltimoCheckpoint()).isEqualTo(t);
        assertThat(service.lerCheckpoint()).contains(t);
    }
}
