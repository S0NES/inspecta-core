package br.com.vistoria.core.modules.financeiro;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.modules.financeiro.FinanceiroRepositories.ExtratoItemRepository;
import br.com.vistoria.core.modules.financeiro.FinanceiroRepositories.FaturaConsolidadaRepository;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V4 (D3/A4): extrato por imobiliária/período + cobrança consolidada.
 *
 * <ul>
 *   <li>Os {@link ExtratoItem} são criados na criação da solicitação B2B
 *       ({@code SolicitacaoService.criar}), não aqui.</li>
 *   <li>{@link #gerarFaturaConsolidada} agrupa os itens em aberto do período numa
 *       {@link FaturaConsolidada} e publica {@link EventTypes#PAYMENT_FATURA_CHARGE_REQUESTED}
 *       — o inspecta-worker cria o checkout do provider e chama de volta
 *       {@link #registrarCheckoutFatura}. {@code PAYMENT_CHARGE_REQUESTED} (por solicitação)
 *       NÃO é usado no fluxo B2B.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExtratoService {

    /** Mesmo fuso de {@code Periodo} — o período de cobrança é por data local, sem hora. */
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final ExtratoItemRepository itens;
    private final FaturaConsolidadaRepository faturas;
    private final OutboxService outbox;

    private Instant inicioDe(LocalDate d) {
        return d.atStartOfDay(ZONE).toInstant();
    }

    /** Fim exclusivo: início do dia seguinte a {@code fim}. */
    private Instant fimDe(LocalDate d) {
        return d.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    /** Linhas de {@code extrato_itens} sem fatura no período, já com endereço/tipo/data. */
    @Transactional(readOnly = true)
    public List<ExtratoItemAberto> listarItensAbertos(UUID imobiliariaId, LocalDate inicio, LocalDate fim) {
        return itens.listarAbertosDetalhado(imobiliariaId, inicioDe(inicio), fimDe(fim));
    }

    /**
     * Soma os itens em aberto do período, cria uma {@link FaturaConsolidada} {@code pendente},
     * carimba {@code fatura_id} em cada item incluído e publica
     * {@link EventTypes#PAYMENT_FATURA_CHARGE_REQUESTED}.
     *
     * @throws IllegalStateException se não houver item em aberto no período
     */
    @Transactional
    public FaturaConsolidada gerarFaturaConsolidada(UUID imobiliariaId, LocalDate inicio, LocalDate fim,
                                                    String criadoPor) {
        return gerarFaturaConsolidada(imobiliariaId, inicio, fim, criadoPor, LocalDate.now(ZONE));
    }

    /**
     * A fatura é o compilado MENSAL do extrato: um mês inteiro (dia 1 ao último dia) e já encerrado.
     * Gerar no meio do mês congelaria o período antes de os demais itens chegarem — e como há uma
     * única fatura por (imobiliária, período), eles ficariam sem fatura.
     *
     * @param hoje data de referência (injetável nos testes)
     * @throws IllegalArgumentException {@code periodo_deve_ser_mes_inteiro} ou {@code mes_ainda_aberto}
     */
    @Transactional
    public FaturaConsolidada gerarFaturaConsolidada(UUID imobiliariaId, LocalDate inicio, LocalDate fim,
                                                    String criadoPor, LocalDate hoje) {
        if (inicio == null || fim == null
                || inicio.getDayOfMonth() != 1 || !fim.equals(inicio.withDayOfMonth(inicio.lengthOfMonth())))
            throw new IllegalArgumentException("periodo_deve_ser_mes_inteiro");
        if (!hoje.isAfter(fim))
            throw new IllegalArgumentException("mes_ainda_aberto");

        List<ExtratoItem> abertos = itens.findAbertos(imobiliariaId, inicioDe(inicio), fimDe(fim))
                .stream().filter(ei -> ei.getFaturaId() == null).toList();
        if (abertos.isEmpty())
            throw new IllegalStateException("sem_itens_abertos_no_periodo");

        BigDecimal total = abertos.stream()
                .map(ExtratoItem::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        FaturaConsolidada fat = new FaturaConsolidada();
        fat.setImobiliariaId(imobiliariaId);
        fat.setPeriodoInicio(inicio);
        fat.setPeriodoFim(fim);
        fat.setValorTotal(total);
        fat = faturas.saveAndFlush(fat);

        for (ExtratoItem ei : abertos) {
            ei.setFaturaId(fat.getId());
            itens.save(ei);
        }

        outbox.publish(EventTypes.PAYMENT_FATURA_CHARGE_REQUESTED, Map.of(
                "eventId", "fatura-charge-" + fat.getId(),
                "faturaId", fat.getId(),
                "imobiliariaId", imobiliariaId,
                "valorTotal", total,
                "criadoPor", criadoPor == null ? "" : criadoPor));
        log.info("[extrato] fatura consolidada {} imob={} itens={} total={} por={}",
                fat.getId(), imobiliariaId, abertos.size(), total, criadoPor);
        return fat;
    }

    /**
     * Callback do inspecta-worker: grava o checkout e marca {@code checkout_ready}.
     * Idempotente: se já está {@code checkout_ready} ou {@code pago}, first-write ganha
     * (redelivery depois do HTTP no provider não sobrescreve).
     */
    @Transactional
    public void registrarCheckoutFatura(UUID faturaId, String checkoutId, String checkoutUrl) {
        FaturaConsolidada fat = get(faturaId);
        if (FaturaConsolidada.Status.checkout_ready.name().equals(fat.getStatus())
                || FaturaConsolidada.Status.pago.name().equals(fat.getStatus())) {
            return;
        }
        fat.setCheckoutId(checkoutId);
        fat.setCheckoutUrl(checkoutUrl);
        fat.setStatus(FaturaConsolidada.Status.checkout_ready.name());
    }

    /** Webhook de pagamento confirmado da fatura. */
    @Transactional
    public void marcarFaturaPaga(UUID faturaId) {
        get(faturaId).setStatus(FaturaConsolidada.Status.pago.name());
    }

    /**
     * Resolve a fatura pelo id do checkout no provedor. Os provedores reais (AbacatePay) mandam
     * {@code data.id} = id do checkout no webhook, não o {@code externalId} que enviamos.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<UUID> faturaIdPorCheckout(String checkoutId) {
        if (checkoutId == null || checkoutId.isBlank()) return java.util.Optional.empty();
        return faturas.findByCheckoutId(checkoutId).map(FaturaConsolidada::getId);
    }

    @Transactional(readOnly = true)
    public List<FaturaConsolidada> listarFaturas(UUID imobiliariaId) {
        return faturas.findByImobiliariaIdOrderByCriadoEmDesc(imobiliariaId);
    }

    private FaturaConsolidada get(UUID faturaId) {
        return faturas.findById(faturaId)
                .orElseThrow(() -> new NotFoundException("FaturaConsolidada " + faturaId));
    }
}
