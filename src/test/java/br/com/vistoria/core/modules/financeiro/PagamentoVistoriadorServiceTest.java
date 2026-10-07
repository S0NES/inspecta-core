package br.com.vistoria.core.modules.financeiro;

import br.com.vistoria.core.modules.financeiro.FinanceiroRepositories.PagamentoVistoriadorRepository;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoService;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoServico;
import br.com.vistoria.core.shared.util.Periodo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PagamentoVistoriadorService")
class PagamentoVistoriadorServiceTest {

    @Mock
    private PagamentoVistoriadorRepository pagamentoRepository;

    @Mock
    private SolicitacaoService solicitacaoService;

    private PagamentoVistoriadorService service;

    @BeforeEach
    void setUp() {
        service = new PagamentoVistoriadorService(pagamentoRepository, solicitacaoService);
    }

    @Test
    @DisplayName("extrato returns correct period data")
    void extrato_returnsPeriodData() {
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        String periodo = "2024-01";

        SolicitacaoServico servico1 = new SolicitacaoServico();
        servico1.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        servico1.setRepasseSnapshot(new BigDecimal("150.00"));

        SolicitacaoServico servico2 = new SolicitacaoServico();
        servico2.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        servico2.setRepasseSnapshot(new BigDecimal("250.00"));

        var range = Periodo.range(periodo);
        when(solicitacaoService.concluidosNoPeriodo(vistoriadorId, range.inicio(), range.fim()))
                .thenReturn(List.of(servico1, servico2));

        var result = service.extrato(vistoriadorId, periodo);

        assertThat(result.periodo()).isEqualTo(periodo);
        assertThat(result.servicos()).hasSize(2);
        assertThat(result.total()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    @DisplayName("extrato sums repasse snapshots correctly")
    void extrato_sumsRepasseSnapshots() {
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000050");
        String periodo = "2024-06";

        SolicitacaoServico s1 = new SolicitacaoServico();
        s1.setRepasseSnapshot(new BigDecimal("100.00"));

        SolicitacaoServico s2 = new SolicitacaoServico();
        s2.setRepasseSnapshot(new BigDecimal("200.50"));

        SolicitacaoServico s3 = new SolicitacaoServico();
        s3.setRepasseSnapshot(new BigDecimal("50.25"));

        var range = Periodo.range(periodo);
        when(solicitacaoService.concluidosNoPeriodo(vistoriadorId, range.inicio(), range.fim()))
                .thenReturn(List.of(s1, s2, s3));

        var result = service.extrato(vistoriadorId, periodo);

        assertThat(result.total()).isEqualByComparingTo(new BigDecimal("350.75"));
    }

    @Test
    @DisplayName("extrato handles null repasse snapshot as zero")
    void extrato_nullRepasseSnapshot() {
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000075");
        String periodo = "2024-03";

        SolicitacaoServico s1 = new SolicitacaoServico();
        s1.setRepasseSnapshot(new BigDecimal("100.00"));

        SolicitacaoServico s2 = new SolicitacaoServico();
        s2.setRepasseSnapshot(null);

        SolicitacaoServico s3 = new SolicitacaoServico();
        s3.setRepasseSnapshot(new BigDecimal("50.00"));

        var range = Periodo.range(periodo);
        when(solicitacaoService.concluidosNoPeriodo(vistoriadorId, range.inicio(), range.fim()))
                .thenReturn(List.of(s1, s2, s3));

        var result = service.extrato(vistoriadorId, periodo);

        assertThat(result.total()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("extrato with no servicos returns zero total")
    void extrato_noServicos() {
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000080");
        String periodo = "2024-04";

        var range = Periodo.range(periodo);
        when(solicitacaoService.concluidosNoPeriodo(vistoriadorId, range.inicio(), range.fim()))
                .thenReturn(List.of());

        var result = service.extrato(vistoriadorId, periodo);

        assertThat(result.servicos()).isEmpty();
        assertThat(result.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("registrar creates payment record")
    void registrar_createsPaymentRecord() {
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        String periodo = "2024-01";
        BigDecimal valor = new BigDecimal("500.00");
        UUID comprovanteArquivoId = UUID.fromString("00000000-0000-0000-0000-000000000999");

        PagamentoVistoriador savedPagamento = new PagamentoVistoriador();
        savedPagamento.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        savedPagamento.setVistoriadorId(vistoriadorId);
        savedPagamento.setPeriodo(periodo);
        savedPagamento.setValor(valor);
        savedPagamento.setComprovanteArquivoId(comprovanteArquivoId);

        when(pagamentoRepository.save(any(PagamentoVistoriador.class))).thenReturn(savedPagamento);

        PagamentoVistoriador result = service.registrar(vistoriadorId, periodo, valor, comprovanteArquivoId);

        assertThat(result.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(result.getVistoriadorId()).isEqualTo(vistoriadorId);
        assertThat(result.getPeriodo()).isEqualTo(periodo);
        assertThat(result.getValor()).isEqualByComparingTo(valor);
        assertThat(result.getComprovanteArquivoId()).isEqualTo(comprovanteArquivoId);
    }

    @Test
    @DisplayName("registrar saves with all fields set correctly")
    void registrar_setsAllFields() {
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000050");
        String periodo = "2024-06";
        BigDecimal valor = new BigDecimal("1500.75");
        UUID comprovanteArquivoId = UUID.fromString("00000000-0000-0000-0000-000000000555");

        when(pagamentoRepository.save(any(PagamentoVistoriador.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PagamentoVistoriador result = service.registrar(vistoriadorId, periodo, valor, comprovanteArquivoId);

        assertThat(result.getVistoriadorId()).isEqualTo(vistoriadorId);
        assertThat(result.getPeriodo()).isEqualTo(periodo);
        assertThat(result.getValor()).isEqualByComparingTo(valor);
        assertThat(result.getComprovanteArquivoId()).isEqualTo(comprovanteArquivoId);
    }

    @Test
    @DisplayName("registrar defaults status to pendente, not pago")
    void registrar_defaultsStatusToPendente() {
        // A payment-to-inspector record that was just created has not actually been paid
        // yet. This mirrors the pagamentos_vistoriador DDL default ('pendente',
        // V3__schema_completo.sql) and the pagamentos_vistoriador_status_ck CHECK
        // ('pendente','pago'). PagamentoVistoriador previously defaulted to "pago", which
        // would mark every freshly-registered payment as already paid.
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        String periodo = "2024-01";
        BigDecimal valor = new BigDecimal("500.00");

        ArgumentCaptor<PagamentoVistoriador> captor = ArgumentCaptor.forClass(PagamentoVistoriador.class);
        when(pagamentoRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.registrar(vistoriadorId, periodo, valor, null);

        assertThat(captor.getValue().getStatus()).isEqualTo("pendente");
    }

    @Test
    @DisplayName("registrar with zero valor")
    void registrar_zeroValor() {
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        String periodo = "2024-01";
        BigDecimal valor = BigDecimal.ZERO;
        UUID comprovanteArquivoId = UUID.fromString("00000000-0000-0000-0000-000000000123");

        when(pagamentoRepository.save(any(PagamentoVistoriador.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PagamentoVistoriador result = service.registrar(vistoriadorId, periodo, valor, comprovanteArquivoId);

        assertThat(result.getValor()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("registrar with large valor")
    void registrar_largeValor() {
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        String periodo = "2024-01";
        BigDecimal valor = new BigDecimal("999999.99");
        UUID comprovanteArquivoId = UUID.fromString("00000000-0000-0000-0000-000000000123");

        when(pagamentoRepository.save(any(PagamentoVistoriador.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PagamentoVistoriador result = service.registrar(vistoriadorId, periodo, valor, comprovanteArquivoId);

        assertThat(result.getValor()).isEqualByComparingTo(new BigDecimal("999999.99"));
    }

    @Test
    @DisplayName("extrato calls solicitacaoService with correct period range")
    void extrato_callsServiceWithCorrectRange() {
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        String periodo = "2024-01";

        var range = Periodo.range(periodo);
        when(solicitacaoService.concluidosNoPeriodo(vistoriadorId, range.inicio(), range.fim()))
                .thenReturn(List.of());

        service.extrato(vistoriadorId, periodo);

        verify(solicitacaoService).concluidosNoPeriodo(eq(vistoriadorId),
                eq(range.inicio()), eq(range.fim()));
    }

    @Test
    @DisplayName("extrato with all null repasse snapshots")
    void extrato_allNullRepasseSnapshots() {
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        String periodo = "2024-01";

        SolicitacaoServico s1 = new SolicitacaoServico();
        s1.setRepasseSnapshot(null);

        SolicitacaoServico s2 = new SolicitacaoServico();
        s2.setRepasseSnapshot(null);

        var range = Periodo.range(periodo);
        when(solicitacaoService.concluidosNoPeriodo(vistoriadorId, range.inicio(), range.fim()))
                .thenReturn(List.of(s1, s2));

        var result = service.extrato(vistoriadorId, periodo);

        assertThat(result.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("registrar then extrato interaction")
    void registrar_and_extrato_interaction() {
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        String periodo = "2024-01";
        BigDecimal valor = new BigDecimal("500.00");
        UUID comprovanteArquivoId = UUID.fromString("00000000-0000-0000-0000-000000000999");

        PagamentoVistoriador pagamento = new PagamentoVistoriador();
        pagamento.setVistoriadorId(vistoriadorId);
        pagamento.setPeriodo(periodo);
        pagamento.setValor(valor);

        when(pagamentoRepository.save(any(PagamentoVistoriador.class))).thenReturn(pagamento);

        var range = Periodo.range(periodo);
        when(solicitacaoService.concluidosNoPeriodo(vistoriadorId, range.inicio(), range.fim()))
                .thenReturn(List.of());

        // Register payment
        service.registrar(vistoriadorId, periodo, valor, comprovanteArquivoId);

        // Get extrato
        var extrato = service.extrato(vistoriadorId, periodo);

        verify(pagamentoRepository).save(any(PagamentoVistoriador.class));
        verify(solicitacaoService).concluidosNoPeriodo(eq(vistoriadorId), any(), any());

        assertThat(extrato.periodo()).isEqualTo(periodo);
    }
}
