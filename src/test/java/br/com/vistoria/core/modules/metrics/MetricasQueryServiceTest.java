package br.com.vistoria.core.modules.metrics;

import br.com.vistoria.core.modules.metrics.MetricsRepositories.EnvioDiarioRepository;
import br.com.vistoria.core.modules.metrics.MetricsRepositories.SolicitacaoDiarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricasQueryService")
class MetricasQueryServiceTest {

    @Mock private EnvioDiarioRepository enviosDiario;
    @Mock private SolicitacaoDiarioRepository solicitacoesDiario;
    private MetricasQueryService service;

    @BeforeEach
    void setUp() {
        service = new MetricasQueryService(enviosDiario, solicitacoesDiario);
    }

    private static MetricaEnvioDiario envioDiario(LocalDate dia, String canal, String comunicacao, long criados) {
        MetricaEnvioDiario d = new MetricaEnvioDiario();
        d.setDia(dia);
        d.setTipoEnvio(canal);
        d.setTipoComunicacao(comunicacao);
        d.setCriados(criados);
        d.setEnviados(criados);
        d.setEntregues(Math.max(0, criados - 1));
        return d;
    }

    @Test
    @DisplayName("envios maps buckets and sums the totals column-wise")
    void envios_totals() {
        LocalDate ini = LocalDate.of(2026, 8, 1);
        LocalDate fim = LocalDate.of(2026, 8, 3);
        when(enviosDiario.buscar(ini, fim, null, null)).thenReturn(List.of(
                envioDiario(ini, "email", "conta", 10),
                envioDiario(fim, "sms", "operacional", 4)));

        var r = service.envios(ini, fim, null, null);

        assertThat(r.buckets()).hasSize(2);
        assertThat(r.totais().criados()).isEqualTo(14);
        assertThat(r.totais().enviados()).isEqualTo(14);
        assertThat(r.inicio()).isEqualTo(ini);
    }

    @Test
    @DisplayName("envios passes blank filters through as null")
    void envios_blankFilters() {
        LocalDate d = LocalDate.of(2026, 8, 1);
        when(enviosDiario.buscar(d, d, null, "conta")).thenReturn(List.of());

        var r = service.envios(d, d, "  ", "conta");

        assertThat(r.buckets()).isEmpty();
        assertThat(r.totais().criados()).isZero();
    }

    @Test
    @DisplayName("solicitacoes maps buckets")
    void solicitacoes_map() {
        LocalDate d = LocalDate.of(2026, 8, 1);
        MetricaSolicitacaoDiario row = new MetricaSolicitacaoDiario();
        row.setDia(d);
        row.setTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000007"));
        row.setTipoServicoNome("Vistoria de entrada");
        row.setConcluidas(3);
        row.setEmAndamento(2);
        when(solicitacoesDiario.buscar(d, d)).thenReturn(List.of(row));

        var r = service.solicitacoes(d, d);

        assertThat(r.buckets()).singleElement().satisfies(b -> {
            assertThat(b.tipoServicoNome()).isEqualTo("Vistoria de entrada");
            assertThat(b.concluidas()).isEqualTo(3);
            assertThat(b.emAndamento()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("rejects inicio after fim, nulls and windows over the cap")
    void validacao() {
        LocalDate a = LocalDate.of(2026, 8, 10);
        LocalDate b = LocalDate.of(2026, 8, 1);
        assertThatThrownBy(() -> service.envios(a, b, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.solicitacoes(null, b))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.envios(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("janela");
    }
}
