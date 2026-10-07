package br.com.vistoria.core.modules.financeiro;

import br.com.vistoria.core.modules.contrato.Contrato;
import br.com.vistoria.core.modules.contrato.PrecoService;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoService;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoServico;
import br.com.vistoria.core.shared.util.Periodo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RelatorioService")
class RelatorioServiceTest {

    @Mock
    private SolicitacaoService solicitacaoService;

    @Mock
    private PrecoService precoService;

    @Mock
    private br.com.vistoria.core.modules.financeiro.FinanceiroRepositories.PagamentoRepository pagamentos;

    @Mock
    private br.com.vistoria.core.modules.imobiliaria.ImobiliariaRepository imobiliarias;

    @Mock
    private br.com.vistoria.core.modules.solicitacao.SolicitacaoRepositories.ServicoRepository servicosRepo;

    @Mock
    private br.com.vistoria.core.modules.laudo.LaudoRepository laudos;

    private RelatorioService service;

    @BeforeEach
    void setUp() {
        service = new RelatorioService(solicitacaoService, precoService, pagamentos, imobiliarias,
                servicosRepo, laudos);
    }

    /** UUID determinístico por número pequeno — legibilidade nos fixtures deste teste. */
    private static UUID uid(long n) {
        return new UUID(0L, n);
    }

    @Test
    @DisplayName("imobiliaria returns correct report data")
    void imobiliaria_returnsReportData() {
        UUID imobiliariaId = uid(100);
        String mes = "2024-01";

        SolicitacaoServico s1 = new SolicitacaoServico();
        s1.setPrecoSnapshot(new BigDecimal("500.00"));

        SolicitacaoServico s2 = new SolicitacaoServico();
        s2.setPrecoSnapshot(new BigDecimal("300.00"));

        Contrato contrato = new Contrato();
        contrato.setFaturamentoMinimo(new BigDecimal("1000.00"));

        var range = Periodo.range(mes);
        when(solicitacaoService.faturamentoNoPeriodo(imobiliariaId, range.inicio(), range.fim()))
                .thenReturn(List.of(s1, s2));
        when(precoService.contratoAtivo(imobiliariaId)).thenReturn(Optional.of(contrato));

        var result = service.imobiliaria(imobiliariaId, mes);

        assertThat(result.imobiliariaId()).isEqualTo(imobiliariaId);
        assertThat(result.mes()).isEqualTo(mes);
        assertThat(result.volume()).isEqualTo(2);
        assertThat(result.faturamento()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(result.faturamentoMinimo()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("imobiliaria applies minimum faturamento when below minimum")
    void imobiliaria_appliesMinimum() {
        UUID imobiliariaId = uid(50);
        String mes = "2024-02";

        SolicitacaoServico s1 = new SolicitacaoServico();
        s1.setPrecoSnapshot(new BigDecimal("300.00"));

        Contrato contrato = new Contrato();
        contrato.setFaturamentoMinimo(new BigDecimal("1000.00"));

        var range = Periodo.range(mes);
        when(solicitacaoService.faturamentoNoPeriodo(imobiliariaId, range.inicio(), range.fim()))
                .thenReturn(List.of(s1));
        when(precoService.contratoAtivo(imobiliariaId)).thenReturn(Optional.of(contrato));

        var result = service.imobiliaria(imobiliariaId, mes);

        assertThat(result.minimoAplicado()).isTrue();
        assertThat(result.faturamentoCobrado()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("imobiliaria does not apply minimum when above minimum")
    void imobiliaria_doesNotApplyMinimum() {
        UUID imobiliariaId = uid(75);
        String mes = "2024-03";

        SolicitacaoServico s1 = new SolicitacaoServico();
        s1.setPrecoSnapshot(new BigDecimal("1500.00"));

        Contrato contrato = new Contrato();
        contrato.setFaturamentoMinimo(new BigDecimal("1000.00"));

        var range = Periodo.range(mes);
        when(solicitacaoService.faturamentoNoPeriodo(imobiliariaId, range.inicio(), range.fim()))
                .thenReturn(List.of(s1));
        when(precoService.contratoAtivo(imobiliariaId)).thenReturn(Optional.of(contrato));

        var result = service.imobiliaria(imobiliariaId, mes);

        assertThat(result.minimoAplicado()).isFalse();
        assertThat(result.faturamentoCobrado()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    @DisplayName("imobiliaria with no contrato (null minimum)")
    void imobiliaria_noContrato() {
        UUID imobiliariaId = uid(80);
        String mes = "2024-04";

        SolicitacaoServico s1 = new SolicitacaoServico();
        s1.setPrecoSnapshot(new BigDecimal("500.00"));

        var range = Periodo.range(mes);
        when(solicitacaoService.faturamentoNoPeriodo(imobiliariaId, range.inicio(), range.fim()))
                .thenReturn(List.of(s1));
        when(precoService.contratoAtivo(imobiliariaId)).thenReturn(Optional.empty());

        var result = service.imobiliaria(imobiliariaId, mes);

        assertThat(result.faturamentoMinimo()).isNull();
        assertThat(result.minimoAplicado()).isFalse();
        assertThat(result.faturamentoCobrado()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("imobiliaria with no servicos returns zero faturamento")
    void imobiliaria_noServicos() {
        UUID imobiliariaId = uid(90);
        String mes = "2024-05";

        var range = Periodo.range(mes);
        when(solicitacaoService.faturamentoNoPeriodo(imobiliariaId, range.inicio(), range.fim()))
                .thenReturn(List.of());
        when(precoService.contratoAtivo(imobiliariaId)).thenReturn(Optional.empty());

        var result = service.imobiliaria(imobiliariaId, mes);

        assertThat(result.volume()).isZero();
        assertThat(result.faturamento()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.faturamentoCobrado()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("imobiliaria sums preco snapshots correctly")
    void imobiliaria_sumsPrecosCorrectly() {
        UUID imobiliariaId = uid(100);
        String mes = "2024-06";

        SolicitacaoServico s1 = new SolicitacaoServico();
        s1.setPrecoSnapshot(new BigDecimal("100.00"));

        SolicitacaoServico s2 = new SolicitacaoServico();
        s2.setPrecoSnapshot(new BigDecimal("200.50"));

        SolicitacaoServico s3 = new SolicitacaoServico();
        s3.setPrecoSnapshot(new BigDecimal("50.25"));

        var range = Periodo.range(mes);
        when(solicitacaoService.faturamentoNoPeriodo(imobiliariaId, range.inicio(), range.fim()))
                .thenReturn(List.of(s1, s2, s3));
        when(precoService.contratoAtivo(imobiliariaId)).thenReturn(Optional.empty());

        var result = service.imobiliaria(imobiliariaId, mes);

        assertThat(result.faturamento()).isEqualByComparingTo(new BigDecimal("350.75"));
        assertThat(result.volume()).isEqualTo(3);
    }

    @Test
    @DisplayName("imobiliaria with exactly minimum faturamento")
    void imobiliaria_exactlyMinimum() {
        UUID imobiliariaId = uid(100);
        String mes = "2024-07";

        SolicitacaoServico s1 = new SolicitacaoServico();
        s1.setPrecoSnapshot(new BigDecimal("1000.00"));

        Contrato contrato = new Contrato();
        contrato.setFaturamentoMinimo(new BigDecimal("1000.00"));

        var range = Periodo.range(mes);
        when(solicitacaoService.faturamentoNoPeriodo(imobiliariaId, range.inicio(), range.fim()))
                .thenReturn(List.of(s1));
        when(precoService.contratoAtivo(imobiliariaId)).thenReturn(Optional.of(contrato));

        var result = service.imobiliaria(imobiliariaId, mes);

        assertThat(result.minimoAplicado()).isFalse(); // Not less than minimum
        assertThat(result.faturamentoCobrado()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("imobiliaria calls services with correct period range")
    void imobiliaria_callsServicesWithCorrectRange() {
        UUID imobiliariaId = uid(100);
        String mes = "2024-01";

        var range = Periodo.range(mes);
        when(solicitacaoService.faturamentoNoPeriodo(imobiliariaId, range.inicio(), range.fim()))
                .thenReturn(List.of());
        when(precoService.contratoAtivo(imobiliariaId)).thenReturn(Optional.empty());

        service.imobiliaria(imobiliariaId, mes);

        verify(solicitacaoService).faturamentoNoPeriodo(eq(imobiliariaId),
                eq(range.inicio()), eq(range.fim()));
        verify(precoService).contratoAtivo(imobiliariaId);
    }


    private FinanceiroRepositories.PagamentoRepository.ConsumoRow row(UUID imobId, long pagos, String total) {
        return new FinanceiroRepositories.PagamentoRepository.ConsumoRow() {
            public UUID getImobiliariaId() { return imobId; }
            public long getServicosPagos() { return pagos; }
            public BigDecimal getValorTotal() { return new BigDecimal(total); }
        };
    }

    private br.com.vistoria.core.modules.imobiliaria.Imobiliaria imob(UUID id, String nome) {
        var i = new br.com.vistoria.core.modules.imobiliaria.Imobiliaria();
        i.setId(id);
        i.setRazaoSocial(nome);
        return i;
    }

    private Contrato contratoComTeto(UUID imobId, Integer volumeMensal) {
        Contrato c = new Contrato();
        c.setImobiliariaId(imobId);
        c.setVolumeMensal(volumeMensal);
        return c;
    }

    private FinanceiroRepositories.PagamentoRepository.PendenteRow pendRow(UUID imobId, long qtd, String total) {
        return new FinanceiroRepositories.PagamentoRepository.PendenteRow() {
            public UUID getImobiliariaId() { return imobId; }
            public long getPagamentosPendentes() { return qtd; }
            public BigDecimal getValorPendente() { return new BigDecimal(total); }
        };
    }

    @Test
    @DisplayName("consumoCrossImobiliaria sinaliza volumeExcedido quando serviços pagos passam do volumeMensal")
    void consumo_volumeExcedido() {
        when(imobiliarias.findAll()).thenReturn(List.of(imob(uid(1), "Viva")));
        var range = Periodo.range("2026-08");
        when(pagamentos.consumoPorImobiliaria(range.inicio(), range.fim()))
                .thenReturn(List.<FinanceiroRepositories.PagamentoRepository.ConsumoRow>of(row(uid(1), 12, "6000.00")));
        when(precoService.contratosAtivos(anyCollection()))
                .thenReturn(java.util.Map.of(uid(1), contratoComTeto(uid(1), 10)));

        var out = service.consumoCrossImobiliaria("2026-08");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).volumeMensal()).isEqualTo(10);
        assertThat(out.get(0).volumeExcedido()).isTrue();
        assertThat(out.get(0).servicosPagos()).isEqualTo(12);
    }

    @Test
    @DisplayName("consumoCrossImobiliaria não sinaliza quando dentro do volume ou sem teto")
    void consumo_dentroDoVolume() {
        when(imobiliarias.findAll()).thenReturn(List.of(imob(uid(1), "Viva"), imob(uid(2), "Lar")));
        var range = Periodo.range("2026-08");
        when(pagamentos.consumoPorImobiliaria(range.inicio(), range.fim()))
                .thenReturn(List.<FinanceiroRepositories.PagamentoRepository.ConsumoRow>of(
                        row(uid(1), 8, "4000.00"), row(uid(2), 30, "9000.00")));
        // imob 2 fica de fora do mapa = sem contrato ativo
        when(precoService.contratosAtivos(anyCollection()))
                .thenReturn(java.util.Map.of(uid(1), contratoComTeto(uid(1), 10)));

        var out = service.consumoCrossImobiliaria("2026-08");

        var viva = out.stream().filter(r -> r.imobiliariaId().equals(uid(1))).findFirst().orElseThrow();
        var lar = out.stream().filter(r -> r.imobiliariaId().equals(uid(2))).findFirst().orElseThrow();
        assertThat(viva.volumeExcedido()).isFalse();
        assertThat(lar.volumeMensal()).isNull();
        assertThat(lar.volumeExcedido()).isFalse();
    }

    @Test
    @DisplayName("consumoCrossImobiliaria inclui imobiliária que só tem cobrança em aberto (§ 5)")
    void consumo_incluiPendente() {
        when(imobiliarias.findAll()).thenReturn(List.of(imob(uid(1), "Viva"), imob(uid(2), "Lar")));
        var range = Periodo.range("2026-08");
        when(pagamentos.consumoPorImobiliaria(range.inicio(), range.fim()))
                .thenReturn(List.<FinanceiroRepositories.PagamentoRepository.ConsumoRow>of(row(uid(1), 5, "2500.00")));
        when(pagamentos.pendentePorImobiliaria(range.inicio(), range.fim()))
                .thenReturn(List.<FinanceiroRepositories.PagamentoRepository.PendenteRow>of(
                        pendRow(uid(1), 2, "1000.00"), pendRow(uid(2), 3, "1500.00")));
        when(precoService.contratosAtivos(anyCollection())).thenReturn(java.util.Map.of());

        var out = service.consumoCrossImobiliaria("2026-08");

        assertThat(out).extracting(r -> r.imobiliariaId()).containsExactlyInAnyOrder(uid(1), uid(2));
        var viva = out.stream().filter(r -> r.imobiliariaId().equals(uid(1))).findFirst().orElseThrow();
        var lar = out.stream().filter(r -> r.imobiliariaId().equals(uid(2))).findFirst().orElseThrow();
        assertThat(viva.servicosPagos()).isEqualTo(5);
        assertThat(viva.pagamentosPendentes()).isEqualTo(2);
        assertThat(viva.valorPendente()).isEqualByComparingTo("1000.00");
        assertThat(lar.servicosPagos()).isZero();
        assertThat(lar.pagamentosPendentes()).isEqualTo(3);
        assertThat(lar.valorPendente()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("imobiliaria with large faturamento values")
    void imobiliaria_largeValues() {
        UUID imobiliariaId = uid(100);
        String mes = "2024-08";

        SolicitacaoServico s1 = new SolicitacaoServico();
        s1.setPrecoSnapshot(new BigDecimal("999999.99"));

        Contrato contrato = new Contrato();
        contrato.setFaturamentoMinimo(new BigDecimal("500000.00"));

        var range = Periodo.range(mes);
        when(solicitacaoService.faturamentoNoPeriodo(imobiliariaId, range.inicio(), range.fim()))
                .thenReturn(List.of(s1));
        when(precoService.contratoAtivo(imobiliariaId)).thenReturn(Optional.of(contrato));

        var result = service.imobiliaria(imobiliariaId, mes);

        assertThat(result.faturamento()).isEqualByComparingTo(new BigDecimal("999999.99"));
        assertThat(result.minimoAplicado()).isFalse();
        assertThat(result.faturamentoCobrado()).isEqualByComparingTo(new BigDecimal("999999.99"));
    }

    @Test
    @DisplayName("vistorias devolve página com total faturado do recorte")
    void vistorias_returnsPageWithTotals() {
        UUID imob = uid(100);
        UUID servico = uid(1);
        var linha = new VistoriaExtratoLinha(
                servico, uid(2), imob, "QA Imóveis", "Rua A", "Entrada",
                "João", SolicitacaoServico.Status.concluida,
                java.time.Instant.parse("2026-09-05T12:00:00Z"), new BigDecimal("320.00"));
        var page = new org.springframework.data.domain.PageImpl<>(List.of(linha));
        when(servicosRepo.listarVistorias(any(), any(), eq(true), eq(imob), eq(false), eq(""), any()))
                .thenReturn(page);
        when(servicosRepo.somarVistorias(any(), any(), eq(true), eq(imob), eq(false), eq("")))
                .thenReturn(new BigDecimal("320.00"));
        when(laudos.findBySolicitacaoServicoIdIn(List.of(servico))).thenReturn(List.of());

        var result = service.vistorias("2026-09", imob, null, org.springframework.data.domain.Pageable.unpaged());

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalFaturado()).isEqualByComparingTo("320.00");
        assertThat(result.content().get(0).endereco()).isEqualTo("Rua A");
        assertThat(result.content().get(0).laudoId()).isNull();
    }
}
