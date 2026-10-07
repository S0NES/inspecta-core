package br.com.vistoria.core.modules.financeiro;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ServicoTipoRepository;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.TipoServicoRepository;
import br.com.vistoria.core.modules.cadastro.Servico;
import br.com.vistoria.core.modules.cadastro.TipoServico;
import br.com.vistoria.core.modules.imobiliaria.Imobiliaria;
import br.com.vistoria.core.modules.imobiliaria.ImobiliariaRepository;
import br.com.vistoria.core.modules.solicitacao.Solicitacao;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoServico;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoRepositories.ServicoRepository;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoRepositories.SolicitacaoRepository;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Camada de dados do checkout com pagamento (AbacatePay: 1 produto por serviço + 1 checkout
 * por solicitação). O I/O com o provider vive no inspecta-worker ({@code PaymentConsumer}); aqui
 * só ficam a leitura do "plano" (o que criar) e a gravação do resultado.
 *
 * <p>Idempotência: {@link #planejar} devolve o {@code externalProductId} já gravado de cada
 * serviço e o {@code checkoutId}/{@code checkoutUrl} já gravados da solicitação, então um
 * retry no inspecta-worker pula as chamadas de provider que já deram certo.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final SolicitacaoRepository solicitacoes;
    private final ServicoRepository servicos;
    private final TipoServicoRepository tiposServico;
    private final br.com.vistoria.core.modules.cadastro.CadastroRepositories.ServicoRepository servicosCatalogo;
    private final ServicoTipoRepository servicoTipos;
    private final ImobiliariaRepository imobiliarias;
    private final br.com.vistoria.core.modules.b2cservico.B2cServiceRepository b2cServicos;
    private final OutboxService outbox;

    /** Um item do checkout = um {@code solicitacao_servico}. */
    public record ItemCheckout(UUID servicoId, UUID productId, String externalProductId,
                               String productName, long precoCentavos) {}

    public record PlanoCheckout(UUID solicitacaoId, List<ItemCheckout> itens, String customerId,
                                String checkoutId, String checkoutUrl) {}

    @Transactional(readOnly = true)
    public PlanoCheckout planejar(UUID solicitacaoId) {
        Solicitacao s = solicitacoes.findById(solicitacaoId)
                .orElseThrow(() -> new NotFoundException("Solicitacao " + solicitacaoId));

        String customerId = s.getImobiliariaId() == null ? null
                : imobiliarias.findById(s.getImobiliariaId())
                        .map(Imobiliaria::getExternalPaymentId)
                        .filter(v -> v != null && !v.isBlank())
                        .orElse(null);

        List<ItemCheckout> itens = new ArrayList<>();
        for (SolicitacaoServico ss : servicos.findBySolicitacaoId(solicitacaoId)) {
            if (ss.getStatus() == SolicitacaoServico.Status.cancelada) continue;
            UUID productId = ss.getProductId() != null ? ss.getProductId() : ss.getId();
            itens.add(new ItemCheckout(ss.getId(), productId, ss.getExternalProductId(),
                    nomeProduto(ss), centavos(ss.getPrecoSnapshot())));
        }
        return new PlanoCheckout(solicitacaoId, itens, customerId, s.getCheckoutId(), s.getCheckoutUrl());
    }

    /**
     * Nome do produto no checkout. B2C: o nome do serviço definido na tela de Biletagem Cliente
     * ({@code b2c_service.nome}) — o cliente B2C não enxerga o Serviço guarda-chuva do B2B.
     * B2B: {@code tipoServico.nome + " - " + servico.nome} (só o tipo quando não há Serviço guarda-chuva).
     */
    private String nomeProduto(SolicitacaoServico ss) {
        if (ss.getB2cServiceId() != null) {
            return b2cServicos.findById(ss.getB2cServiceId())
                    .map(br.com.vistoria.core.modules.b2cservico.B2cService::getNome)
                    .orElseGet(() -> nomeProdutoB2b(ss.getTipoServicoId()));
        }
        return nomeProdutoB2b(ss.getTipoServicoId());
    }

    private String nomeProdutoB2b(UUID tipoServicoId) {
        String tipo = tiposServico.findById(tipoServicoId).map(TipoServico::getNome).orElse("Servico");
        String servico = servicoTipos.findByTipoServicoId(tipoServicoId).stream()
                .findFirst()
                .flatMap(st -> servicosCatalogo.findById(st.getServicoId()))
                .map(Servico::getNome)
                .orElse(null);
        return servico == null ? tipo : tipo + " - " + servico;
    }

    private static long centavos(BigDecimal valor) {
        return valor.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    @Transactional
    public void registrarProdutoExterno(UUID servicoId, String externalProductId) {
        SolicitacaoServico ss = servicos.findById(servicoId)
                .orElseThrow(() -> new NotFoundException("Servico " + servicoId));
        ss.setExternalProductId(externalProductId);
    }

    /**
     * Grava o checkout na solicitação, marca {@code payment_status='checkout_ready'} e publica
     * {@code jobs.payment.checkout-ready}.
     */
    @Transactional
    public void registrarCheckoutPronto(UUID solicitacaoId, String checkoutId, String checkoutUrl) {
        Solicitacao s = solicitacoes.findById(solicitacaoId)
                .orElseThrow(() -> new NotFoundException("Solicitacao " + solicitacaoId));
        s.setCheckoutId(checkoutId);
        s.setCheckoutUrl(checkoutUrl);
        s.setPaymentStatus("checkout_ready");
        outbox.publish(EventTypes.PAYMENT_CHECKOUT_READY, Map.of(
                "eventId", "checkout-ready-" + solicitacaoId,
                "solicitacaoId", solicitacaoId,
                "checkoutId", checkoutId == null ? "" : checkoutId,
                "checkoutUrl", checkoutUrl == null ? "" : checkoutUrl));
        log.info("[checkout] solicitacao={} checkout_ready checkoutId={}", solicitacaoId, checkoutId);
    }

    @Transactional
    public void marcarErro(UUID solicitacaoId) {
        solicitacoes.findById(solicitacaoId).ifPresent(s -> {
            // Não sobrescreve um checkout que já ficou pronto/pago numa passada anterior.
            if (!"checkout_ready".equals(s.getPaymentStatus()) && !"pago".equals(s.getPaymentStatus())) {
                s.setPaymentStatus("erro");
            }
        });
    }
}
