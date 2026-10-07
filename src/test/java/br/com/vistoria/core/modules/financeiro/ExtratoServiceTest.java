package br.com.vistoria.core.modules.financeiro;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.modules.financeiro.FinanceiroRepositories.ExtratoItemRepository;
import br.com.vistoria.core.modules.financeiro.FinanceiroRepositories.FaturaConsolidadaRepository;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExtratoService")
class ExtratoServiceTest {

    @Mock private ExtratoItemRepository itens;
    @Mock private FaturaConsolidadaRepository faturas;
    @Mock private OutboxService outbox;

    private ExtratoService service;

    private static final UUID IMOB = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 9, 30);
    /** "Hoje" fixo: 1º dia útil depois de FIM — o mês de setembro já está encerrado. */
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 1);

    @BeforeEach
    void setUp() {
        service = new ExtratoService(itens, faturas, outbox);
    }

    private ExtratoItem item(String valor) {
        ExtratoItem ei = new ExtratoItem();
        ei.setId(UUID.randomUUID());
        ei.setImobiliariaId(IMOB);
        ei.setSolicitacaoServicoId(UUID.randomUUID());
        ei.setValor(new BigDecimal(valor));
        return ei;
    }

    @Test
    @DisplayName("listarItensAbertos delega ao join detalhado")
    void listarItensAbertos_delega() {
        when(itens.listarAbertosDetalhado(eq(IMOB), any(), any())).thenReturn(List.of());
        assertThat(service.listarItensAbertos(IMOB, INICIO, FIM)).isEmpty();
    }

    @Test
    @DisplayName("gerarFaturaConsolidada soma itens, cria fatura pendente, associa itens e publica o evento novo")
    void gerarFaturaConsolidada_ok() {
        ExtratoItem a = item("100.00");
        ExtratoItem b = item("250.50");
        when(itens.findAbertos(eq(IMOB), any(), any())).thenReturn(List.of(a, b));
        when(faturas.saveAndFlush(any(FaturaConsolidada.class))).thenAnswer(i -> {
            FaturaConsolidada f = i.getArgument(0);
            f.setId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
            return f;
        });

        FaturaConsolidada fat = service.gerarFaturaConsolidada(IMOB, INICIO, FIM, "user-1", HOJE);

        assertThat(fat.getValorTotal()).isEqualByComparingTo("350.50");
        assertThat(fat.getStatus()).isEqualTo("pendente");
        assertThat(fat.getPeriodoInicio()).isEqualTo(INICIO);
        assertThat(fat.getPeriodoFim()).isEqualTo(FIM);

        assertThat(a.getFaturaId()).isEqualTo(fat.getId());
        assertThat(b.getFaturaId()).isEqualTo(fat.getId());
        verify(itens, org.mockito.Mockito.times(2)).save(any(ExtratoItem.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> ev = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(eq(EventTypes.PAYMENT_FATURA_CHARGE_REQUESTED), ev.capture());
        assertThat(ev.getValue()).containsEntry("faturaId", fat.getId());
        assertThat(ev.getValue()).containsEntry("imobiliariaId", IMOB);
        assertThat(ev.getValue().get("eventId")).isEqualTo("fatura-charge-" + fat.getId());
    }

    @Test
    @DisplayName("gerarFaturaConsolidada sem itens em aberto → IllegalStateException, nada publicado")
    void gerarFaturaConsolidada_vazio() {
        when(itens.findAbertos(eq(IMOB), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.gerarFaturaConsolidada(IMOB, INICIO, FIM, "user-1", HOJE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("sem_itens_abertos_no_periodo");
        verify(faturas, never()).saveAndFlush(any());
        verify(outbox, never()).publish(any(), any());
    }

    @Test
    @DisplayName("gerarFaturaConsolidada ignora itens que já têm fatura_id (corrida pós-lock) e não publica")
    void gerarFaturaConsolidada_itensJaVinculados_naoPublica() {
        ExtratoItem a = item("100.00");
        a.setFaturaId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
        when(itens.findAbertos(eq(IMOB), any(), any())).thenReturn(List.of(a));

        assertThatThrownBy(() -> service.gerarFaturaConsolidada(IMOB, INICIO, FIM, "user-1", HOJE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("sem_itens_abertos_no_periodo");
        verify(faturas, never()).saveAndFlush(any());
        verify(outbox, never()).publish(any(), any());
    }

    @Test
    @DisplayName("gerarFaturaConsolidada recusa período que não é um mês inteiro (fatura é o compilado mensal)")
    void gerarFaturaConsolidada_periodoNaoEMesInteiro() {
        assertThatThrownBy(() -> service.gerarFaturaConsolidada(IMOB, LocalDate.of(2026, 9, 5), FIM, "u", HOJE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("periodo_deve_ser_mes_inteiro");
        assertThatThrownBy(() -> service.gerarFaturaConsolidada(IMOB, INICIO, LocalDate.of(2026, 9, 15), "u", HOJE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("periodo_deve_ser_mes_inteiro");
        assertThatThrownBy(() -> service.gerarFaturaConsolidada(IMOB, INICIO, LocalDate.of(2026, 10, 31), "u", HOJE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("periodo_deve_ser_mes_inteiro");
        verify(faturas, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("gerarFaturaConsolidada recusa mês ainda em andamento (último dia incluso) — itens ainda podem chegar")
    void gerarFaturaConsolidada_mesAindaAberto() {
        assertThatThrownBy(() -> service.gerarFaturaConsolidada(IMOB, INICIO, FIM, "u", FIM))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("mes_ainda_aberto");
        assertThatThrownBy(() -> service.gerarFaturaConsolidada(IMOB, INICIO, FIM, "u", LocalDate.of(2026, 9, 10)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("mes_ainda_aberto");
        verify(faturas, never()).saveAndFlush(any());
        verify(outbox, never()).publish(any(), any());
    }

    @Test
    @DisplayName("gerarFaturaConsolidada aceita fevereiro de ano não bissexto (28 dias) e o mês seguinte já encerrado")
    void gerarFaturaConsolidada_fevereiro() {
        when(itens.findAbertos(eq(IMOB), any(), any())).thenReturn(List.of(item("10.00")));
        when(faturas.saveAndFlush(any(FaturaConsolidada.class))).thenAnswer(i -> {
            FaturaConsolidada saved = i.getArgument(0);
            saved.setId(UUID.fromString("00000000-0000-0000-0000-0000000000f2"));
            return saved;
        });

        FaturaConsolidada f = service.gerarFaturaConsolidada(IMOB, LocalDate.of(2027, 2, 1),
                LocalDate.of(2027, 2, 28), "u", LocalDate.of(2027, 3, 1));

        assertThat(f.getPeriodoFim()).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    @DisplayName("registrarCheckoutFatura grava checkout e marca checkout_ready")
    void registrarCheckoutFatura_ok() {
        FaturaConsolidada fat = new FaturaConsolidada();
        fat.setId(UUID.fromString("00000000-0000-0000-0000-0000000000f2"));
        when(faturas.findById(fat.getId())).thenReturn(Optional.of(fat));

        service.registrarCheckoutFatura(fat.getId(), "chk_1", "https://pay/chk_1");

        assertThat(fat.getCheckoutId()).isEqualTo("chk_1");
        assertThat(fat.getCheckoutUrl()).isEqualTo("https://pay/chk_1");
        assertThat(fat.getStatus()).isEqualTo("checkout_ready");
    }

    @Test
    @DisplayName("registrarCheckoutFatura em checkout_ready/pago é no-op (first-write ganha no redelivery)")
    void registrarCheckoutFatura_jaPronto_noop() {
        FaturaConsolidada fat = new FaturaConsolidada();
        fat.setId(UUID.fromString("00000000-0000-0000-0000-0000000000f2"));
        fat.setStatus(FaturaConsolidada.Status.checkout_ready.name());
        fat.setCheckoutId("chk_original");
        fat.setCheckoutUrl("https://pay/original");
        when(faturas.findById(fat.getId())).thenReturn(Optional.of(fat));

        service.registrarCheckoutFatura(fat.getId(), "chk_novo", "https://pay/novo");

        assertThat(fat.getCheckoutId()).isEqualTo("chk_original");
        assertThat(fat.getCheckoutUrl()).isEqualTo("https://pay/original");
        assertThat(fat.getStatus()).isEqualTo("checkout_ready");
    }

    @Test
    @DisplayName("marcarFaturaPaga marca pago")
    void marcarFaturaPaga_ok() {
        FaturaConsolidada fat = new FaturaConsolidada();
        fat.setId(UUID.fromString("00000000-0000-0000-0000-0000000000f3"));
        when(faturas.findById(fat.getId())).thenReturn(Optional.of(fat));

        service.marcarFaturaPaga(fat.getId());

        assertThat(fat.getStatus()).isEqualTo("pago");
    }

    @Test
    @DisplayName("marcarFaturaPaga em fatura inexistente → NotFoundException")
    void marcarFaturaPaga_notFound() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000f9");
        when(faturas.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.marcarFaturaPaga(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("listarFaturas delega")
    void listarFaturas_delega() {
        when(faturas.findByImobiliariaIdOrderByCriadoEmDesc(IMOB)).thenReturn(List.of());
        assertThat(service.listarFaturas(IMOB)).isEmpty();
    }
}
