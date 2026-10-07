package br.com.vistoria.core.modules.financeiro;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.modules.cadastro.ClienteService;
import br.com.vistoria.core.modules.financeiro.FinanceiroRepositories.PagamentoRepository;
import br.com.vistoria.core.modules.imobiliaria.ImobiliariaRepository;
import br.com.vistoria.core.modules.notify.NotifyTemplates;
import br.com.vistoria.core.modules.solicitacao.Solicitacao;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoService;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import br.com.vistoria.core.shared.webhook.WebhookRecebidoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagamentoService {

    private final PagamentoRepository pagamentos;
    private final SolicitacaoService solicitacaoService;
    private final ClienteService clienteService;
    private final br.com.vistoria.core.modules.clienteb2c.ClienteB2cRepository clientesB2c;
    private final ImobiliariaRepository imobiliarias;
    private final WebhookRecebidoService webhookRecebidoService;
    private final OutboxService outbox;

    /**
     * {@code checkoutId} = id do checkout no provider (== {@code Pagamento.externalId} após o
     * inspecta-worker criar o checkout); {@code checkoutReady} = há URL de checkout e ainda não foi pago.
     */
    public record Cobranca(UUID pagamentoId, String status, String checkoutUrl,
                           String checkoutId, boolean checkoutReady,
                           String pixBrCode, String pixQrCodeBase64, Instant pixExpiraEm) {
        public Cobranca(UUID pagamentoId, String status, String checkoutUrl) {
            this(pagamentoId, status, checkoutUrl, null,
                 checkoutUrl != null && !Pagamento.Status.pago.name().equals(status), null, null, null);
        }
        static Cobranca de(Pagamento p) {
            return new Cobranca(p.getId(), p.getStatus(), p.getCheckoutUrl(), p.getExternalId(),
                    p.getCheckoutUrl() != null && !Pagamento.Status.pago.name().equals(p.getStatus()),
                    p.getPixBrCode(), p.getPixQrBase64(), p.getPixExpiraEm());
        }
    }

    /** Dados do pagador exigidos pelo PIX transparente (todos obrigatórios quando informados). */
    public record Pagador(String nome, String email, String cpf, String telefone) {}

    /**
     * Pede um PIX transparente para a solicitação (B2C). Reaproveita o PIX vigente (não pago e
     * não expirado); senão publica {@code jobs.payment.pix-requested} para o inspecta-worker gerar
     * outro. Devolve a cobrança no estado atual — a UI faz polling do {@link #consultar}.
     */
    @Transactional
    public Cobranca solicitarPix(UUID solicitacaoId) {
        Pagamento p = pagamentos.findFirstBySolicitacaoIdOrderByCriadoEmDesc(solicitacaoId)
                .filter(x -> !Pagamento.Status.falhou.name().equals(x.getStatus()))
                .orElse(null);
        if (p == null) {
            BigDecimal valor = solicitacaoService.totalServicos(solicitacaoId);
            if (valor.compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalStateException("Solicitacao sem valor a cobrar");
            p = new Pagamento();
            p.setSolicitacaoId(solicitacaoId);
            p.setProvider("pendente");
            p.setValor(valor);
            p = pagamentos.saveAndFlush(p);
        }
        if (Pagamento.Status.pago.name().equals(p.getStatus())) return Cobranca.de(p);

        boolean pixVigente = p.getPixBrCode() != null
                && p.getPixExpiraEm() != null && p.getPixExpiraEm().isAfter(Instant.now());
        if (!pixVigente && !pixEmGeracao(p)) {
            // Expirado/inexistente: limpa e pede outro. O outbox não deduplica por eventId, então marcamos
            // "em geração" no pixId para o polling/duplo clique não disparar dois PIX seguidos.
            p.setPixId(PIX_EM_GERACAO + System.currentTimeMillis());
            p.setPixBrCode(null);
            p.setPixQrBase64(null);
            p.setPixExpiraEm(null);
            outbox.publish(EventTypes.PAYMENT_PIX_REQUESTED, Map.of(
                    "eventId", "pix-" + p.getId() + "-" + System.currentTimeMillis(),
                    "pagamentoId", p.getId(),
                    "solicitacaoId", solicitacaoId,
                    "valor", p.getValor().toString()));
        }
        return Cobranca.de(p);
    }

    private static final String PIX_EM_GERACAO = "gerando-";
    private static final long PIX_EM_GERACAO_MS = 30_000;

    /** Há um pedido de PIX publicado há menos de 30 s ainda sem resposta do provedor. */
    private static boolean pixEmGeracao(Pagamento p) {
        String id = p.getPixId();
        if (id == null || !id.startsWith(PIX_EM_GERACAO) || p.getPixBrCode() != null) return false;
        try {
            return System.currentTimeMillis() - Long.parseLong(id.substring(PIX_EM_GERACAO.length())) < PIX_EM_GERACAO_MS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Chamado pelo inspecta-worker quando o provedor devolveu o PIX. */
    @Transactional
    public void registrarPix(UUID pagamentoId, String provider, String pixId, String brCode,
                             String qrBase64, Instant expiraEm) {
        Pagamento p = pagamentos.findById(pagamentoId)
                .orElseThrow(() -> new NotFoundException("Pagamento " + pagamentoId));
        if (Pagamento.Status.pago.name().equals(p.getStatus())) return;
        p.setProvider(provider);
        p.setPixId(pixId);
        p.setPixBrCode(brCode);
        p.setPixQrBase64(qrBase64);
        p.setPixExpiraEm(expiraEm);
        p.setStatus(Pagamento.Status.pendente.name());
    }

    /** PIX pendentes e ainda válidos — a reconciliação confere cada um no provedor. */
    @Transactional(readOnly = true)
    public List<Pagamento> pixPendentes() {
        return pagamentos.findByStatusAndPixIdIsNotNullAndPixExpiraEmAfter(
                Pagamento.Status.pendente.name(), Instant.now());
    }

    /** Pagador B2C (cliente do clientes_b2c); {@code null} se a solicitação não tem dados completos. */
    @Transactional(readOnly = true)
    public Pagador pagadorDe(UUID solicitacaoId) {
        Solicitacao sol = solicitacaoService.get(solicitacaoId);
        if (sol.getClienteB2cId() == null) return null;
        return clientesB2c.findById(sol.getClienteB2cId())
                .filter(c -> notBlank(c.getNome()) && notBlank(c.getEmail())
                        && notBlank(c.getCpf()) && notBlank(c.getTelefone()))
                .map(c -> new Pagador(c.getNome(), c.getEmail(), c.getCpf(), c.getTelefone()))
                .orElse(null);
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    @Transactional
    public Cobranca criarCobranca(UUID solicitacaoId) {
        Solicitacao sol = solicitacaoService.get(solicitacaoId);

        // Uma cobrança em aberto por solicitação (idempotência do auto-disparo na criação).
        var existente = pagamentos.findFirstBySolicitacaoIdOrderByCriadoEmDesc(solicitacaoId)
                .filter(p -> !Pagamento.Status.falhou.name().equals(p.getStatus()));
        if (existente.isPresent()) {
            Pagamento p = existente.get();
            // Retrigger manual (POST /v1/solicitacoes/{id}/pagamento) OU auto-charge após um erro
            // de provider: re-publica a preparação do checkout. Idempotente no inspecta-worker — serviço
            // que já tem external_product_id e solicitação que já tem checkout_id são pulados.
            if (!Pagamento.Status.pago.name().equals(p.getStatus())) {
                outbox.publish(EventTypes.PAYMENT_CHARGE_REQUESTED, Map.of(
                        "eventId", "charge-" + p.getId(),
                        "pagamentoId", p.getId(),
                        "solicitacaoId", solicitacaoId,
                        "valor", p.getValor().toString()));
            }
            return new Cobranca(p.getId(), p.getStatus(), p.getCheckoutUrl());
        }

        BigDecimal valor = solicitacaoService.totalServicos(solicitacaoId);
        if (valor.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalStateException("Solicitacao sem valor a cobrar");

        Pagamento p = new Pagamento();
        p.setSolicitacaoId(solicitacaoId);
        p.setProvider("pendente");
        p.setValor(valor);
        p = pagamentos.saveAndFlush(p);

        outbox.publish(EventTypes.PAYMENT_CHARGE_REQUESTED, Map.of(
                "eventId", "charge-" + p.getId(),
                "pagamentoId", p.getId(),
                "solicitacaoId", solicitacaoId,
                "valor", valor.toString()));
        return new Cobranca(p.getId(), p.getStatus(), null);
    }

    @Transactional(readOnly = true)
    public Cobranca consultar(UUID solicitacaoId) {
        Pagamento p = pagamentos.findFirstBySolicitacaoIdOrderByCriadoEmDesc(solicitacaoId)
                .orElseThrow(() -> new NotFoundException("Sem cobranca para solicitacao " + solicitacaoId));
        return Cobranca.de(p);
    }

    @Transactional
    public void receberWebhook(String provider, String eventId, String payload, Map<String, String> headers) {
        try {
            if (eventId == null || eventId.isBlank()) {
                log.warn("Webhook pagamento sem eventId provider={}", provider);
                return;
            }
            webhookRecebidoService.receber("pagamento-" + provider, eventId, payload)
                    .ifPresent(w -> outbox.publish(EventTypes.PAYMENT_WEBHOOK, Map.of(
                            "eventId", eventId,
                            "provider", provider,
                            "payload", payload,
                            "headers", headers == null ? Map.of() : headers)));
        } catch (Exception e) {
            // Pode ser payload malformado OU falha de infra (banco/outbox indisponivel). Nos dois
            // casos o webhook nao foi persistido/publicado — investigar antes de assumir "lixo do provedor".
            log.error("Falha ao processar webhook pagamento (payload invalido ou erro de infra) provider={}", provider, e);
        }
    }

    @Transactional
    public void aplicarCharge(UUID pagamentoId, String provider, String externalId, String checkoutUrl) {
        Pagamento p = pagamentos.findById(pagamentoId)
                .orElseThrow(() -> new NotFoundException("Pagamento " + pagamentoId));
        p.setProvider(provider);
        p.setExternalId(externalId);
        p.setCheckoutUrl(checkoutUrl);
        p.setStatus(Pagamento.Status.pendente.name());
    }

    @Transactional
    public void aplicarEvento(String eventId, String externalId, String status) {
        Pagamento p = pagamentos.findByExternalId(externalId)
                .or(() -> pagamentos.findByPixId(externalId)).orElse(null);
        if (p == null) {
            log.warn("Webhook pagamento sem cobranca correspondente externalId={}", externalId);
            return;
        }
        boolean eraPago = Pagamento.Status.pago.name().equals(p.getStatus());
        p.setStatus(status);
        p.setEventId(eventId);

        if (!eraPago && Pagamento.Status.pago.name().equals(status)) {
            // Pagamento confirmado É o gate da atribuição agora: marca payment_status='pago' e
            // publica um jobs.assignment.requested por serviço ainda pendente. Idempotente — o
            // guard !eraPago garante que um replay do webhook pago não chega aqui de novo.
            int atribuicoes = solicitacaoService.aplicarPagamentoConfirmado(p.getSolicitacaoId());
            log.info("[payment] solicitacao {} paga -> {} atribuicoes publicadas", p.getSolicitacaoId(), atribuicoes);

            String destinatario = emailDoDestinatario(p.getSolicitacaoId());
            if (destinatario != null) {
                outbox.publish("notify.requested.pagamento-confirmado", Map.of(
                        "eventId", "pay-notify-" + p.getId(),
                        "canal", "email",
                        "templateId", NotifyTemplates.PAGAMENTO_CONFIRMADO,
                        "destinatario", destinatario,
                        "variaveis", Map.of("solicitacaoId", p.getSolicitacaoId(), "valor", p.getValor().toString())));
            } else {
                log.warn("Cliente sem email, notificacao de pagamento nao enviada solicitacaoId={}", p.getSolicitacaoId());
            }
        }
    }

    /**
     * Destinatário da cobrança/confirmação: e-mail financeiro da imobiliária do imóvel; se ela
     * não tiver, cai no e-mail do cliente B2C ou do cliente (B2B) da solicitação. {@code null} quando nenhum existe.
     */
    @Transactional(readOnly = true)
    public String emailDoDestinatario(UUID solicitacaoId) {
        Solicitacao sol = solicitacaoService.get(solicitacaoId);
        String financeiro = sol.getImobiliariaId() == null ? null
                : imobiliarias.findById(sol.getImobiliariaId())
                        .map(i -> i.getEmailFinanceiro())
                        .filter(e -> e != null && !e.isBlank())
                        .orElse(null);
        if (financeiro != null) return financeiro;
        // B2C: a solicitação não tem imobiliária nem cliente B2B — o pagador é o clientes_b2c.
        if (sol.getClienteB2cId() != null) {
            return clientesB2c.findById(sol.getClienteB2cId())
                    .map(br.com.vistoria.core.modules.clienteb2c.ClienteB2c::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .orElse(null);
        }
        return clienteService.emailPorId(sol.getClienteId()).orElse(null);
    }
}
