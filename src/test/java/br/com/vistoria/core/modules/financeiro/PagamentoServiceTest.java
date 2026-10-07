package br.com.vistoria.core.modules.financeiro;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.modules.cadastro.ClienteService;
import br.com.vistoria.core.modules.solicitacao.Solicitacao;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoService;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import br.com.vistoria.core.shared.webhook.WebhookRecebidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PagamentoService")
class PagamentoServiceTest {

    @Mock
    private FinanceiroRepositories.PagamentoRepository pagamentos;

    @Mock
    private SolicitacaoService solicitacaoService;

    @Mock
    private ClienteService clienteService;

    @Mock
    private br.com.vistoria.core.modules.imobiliaria.ImobiliariaRepository imobiliarias;

    @Mock
    private WebhookRecebidoService webhookRecebidoService;

    @Mock
    private OutboxService outbox;

    @Mock private br.com.vistoria.core.modules.clienteb2c.ClienteB2cRepository clientesB2c;

    private PagamentoService service;

    @BeforeEach
    void setUp() {
        service = new PagamentoService(pagamentos, solicitacaoService, clienteService, clientesB2c,
                imobiliarias, webhookRecebidoService, outbox);
    }

    @Test
    @DisplayName("criarCobranca creates payment and publishes event")
    void criarCobranca_success() {
        Solicitacao sol = new Solicitacao();
        sol.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        sol.setImobiliariaId(null); // B2C

        when(solicitacaoService.get(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(sol);
        when(solicitacaoService.totalServicos(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(new BigDecimal("100.00"));

        Pagamento pag = new Pagamento();
        pag.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        pag.setStatus(Pagamento.Status.pendente.name());
        pag.setValor(new BigDecimal("100.00"));
        pag.setSolicitacaoId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        when(pagamentos.saveAndFlush(any(Pagamento.class))).thenReturn(pag);

        PagamentoService.Cobranca result = service.criarCobranca(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThat(result.pagamentoId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        assertThat(result.status()).isEqualTo(Pagamento.Status.pendente.name());

        verify(outbox).publish(any(String.class), any());
    }

    @Test
    @DisplayName("criarCobranca gera cobranca tambem para B2B (link vai para a imobiliaria)")
    void criarCobranca_b2b_gera() {
        Solicitacao sol = new Solicitacao();
        sol.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        sol.setImobiliariaId(UUID.fromString("00000000-0000-0000-0000-000000000100")); // B2B

        when(solicitacaoService.get(UUID.fromString("00000000-0000-0000-0000-000000000002"))).thenReturn(sol);
        when(pagamentos.findFirstBySolicitacaoIdOrderByCriadoEmDesc(UUID.fromString("00000000-0000-0000-0000-000000000002"))).thenReturn(Optional.empty());
        when(solicitacaoService.totalServicos(UUID.fromString("00000000-0000-0000-0000-000000000002"))).thenReturn(new java.math.BigDecimal("150.00"));
        when(pagamentos.saveAndFlush(any())).thenAnswer(inv -> {
            Pagamento p = inv.getArgument(0);
            p.setId(UUID.fromString("00000000-0000-0000-0000-000000000009"));
            return p;
        });

        var cobranca = service.criarCobranca(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        assertThat(cobranca.pagamentoId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000009"));
        verify(outbox).publish(any(String.class), any());
    }

    @Test
    @DisplayName("criarCobranca throws when valor is zero or negative")
    void criarCobranca_noValor_throws() {
        Solicitacao sol = new Solicitacao();
        sol.setId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        sol.setImobiliariaId(null);

        when(solicitacaoService.get(UUID.fromString("00000000-0000-0000-0000-000000000003"))).thenReturn(sol);
        when(solicitacaoService.totalServicos(UUID.fromString("00000000-0000-0000-0000-000000000003"))).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.criarCobranca(UUID.fromString("00000000-0000-0000-0000-000000000003")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valor");
    }

    @Test
    @DisplayName("consultar retrieves payment by solicitacao")
    void consultar_found() {
        Pagamento pag = new Pagamento();
        pag.setId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        pag.setStatus(Pagamento.Status.pendente.name());
        pag.setCheckoutUrl("https://checkout.com");

        when(pagamentos.findFirstBySolicitacaoIdOrderByCriadoEmDesc(UUID.fromString("00000000-0000-0000-0000-000000000005")))
                .thenReturn(Optional.of(pag));

        PagamentoService.Cobranca result = service.consultar(UUID.fromString("00000000-0000-0000-0000-000000000005"));

        assertThat(result.pagamentoId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        assertThat(result.checkoutUrl()).isEqualTo("https://checkout.com");
    }

    @Test
    @DisplayName("consultar throws when not found")
    void consultar_notFound() {
        when(pagamentos.findFirstBySolicitacaoIdOrderByCriadoEmDesc(UUID.fromString("00000000-0000-0000-0000-000000000999")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(UUID.fromString("00000000-0000-0000-0000-000000000999")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("aplicarCharge updates payment with provider details")
    void aplicarCharge_updates() {
        Pagamento pag = new Pagamento();
        pag.setId(UUID.fromString("00000000-0000-0000-0000-000000000030"));

        when(pagamentos.findById(UUID.fromString("00000000-0000-0000-0000-000000000030"))).thenReturn(Optional.of(pag));

        service.aplicarCharge(UUID.fromString("00000000-0000-0000-0000-000000000030"), "stripe", "ch-123", "https://stripe.com/pay");

        assertThat(pag.getProvider()).isEqualTo("stripe");
        assertThat(pag.getExternalId()).isEqualTo("ch-123");
        assertThat(pag.getCheckoutUrl()).isEqualTo("https://stripe.com/pay");
        assertThat(pag.getStatus()).isEqualTo(Pagamento.Status.pendente.name());
    }

    @Test
    @DisplayName("aplicarCharge throws when pagamento not found")
    void aplicarCharge_notFound() {
        when(pagamentos.findById(UUID.fromString("00000000-0000-0000-0000-000000000999"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aplicarCharge(UUID.fromString("00000000-0000-0000-0000-000000000999"), "stripe", "ch-123", "url"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("aplicarEvento updates status pago and libera solicitacao")
    void aplicarEvento_pago_liberation() {
        Pagamento pag = new Pagamento();
        pag.setId(UUID.fromString("00000000-0000-0000-0000-000000000040"));
        pag.setStatus(Pagamento.Status.pendente.name());
        pag.setSolicitacaoId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        pag.setValor(new BigDecimal("100.00"));

        when(pagamentos.findByExternalId("ext-456")).thenReturn(Optional.of(pag));

        Solicitacao sol = new Solicitacao();
        sol.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        sol.setClienteId(UUID.fromString("00000000-0000-0000-0000-000000000099"));

        when(solicitacaoService.get(UUID.fromString("00000000-0000-0000-0000-000000000010"))).thenReturn(sol);
        when(clienteService.emailPorId(UUID.fromString("00000000-0000-0000-0000-000000000099"))).thenReturn(Optional.of("client@example.com"));

        service.aplicarEvento("evt-456", "ext-456", Pagamento.Status.pago.name());

        assertThat(pag.getStatus()).isEqualTo(Pagamento.Status.pago.name());
        assertThat(pag.getEventId()).isEqualTo("evt-456");

        verify(solicitacaoService).aplicarPagamentoConfirmado(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        verify(outbox, times(1)).publish(any(String.class), any());
    }

    @Test
    @DisplayName("aplicarEvento with eraPago=true does not liberate (idempotent)")
    void aplicarEvento_replay_noLiberation() {
        Pagamento pag = new Pagamento();
        pag.setId(UUID.fromString("00000000-0000-0000-0000-000000000050"));
        pag.setStatus(Pagamento.Status.pago.name()); // Already paid
        pag.setSolicitacaoId(UUID.fromString("00000000-0000-0000-0000-000000000011"));

        when(pagamentos.findByExternalId("ext-789")).thenReturn(Optional.of(pag));

        service.aplicarEvento("evt-789", "ext-789", Pagamento.Status.pago.name());

        // eraPago era true -> nenhum efeito colateral (nem notify)
        verify(outbox, never()).publish(anyString(), any());

        // Status is still pago (no change)
        assertThat(pag.getStatus()).isEqualTo(Pagamento.Status.pago.name());
    }

    @Test
    @DisplayName("aplicarEvento with no matching pagamento logs warning")
    void aplicarEvento_noMatch() {
        when(pagamentos.findByExternalId("ext-notfound")).thenReturn(Optional.empty());

        service.aplicarEvento("evt-notfound", "ext-notfound", Pagamento.Status.pago.name());

        // Should handle gracefully without throwing
        verify(outbox, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("aplicarEvento with status other than pago just updates")
    void aplicarEvento_pendente() {
        Pagamento pag = new Pagamento();
        pag.setId(UUID.fromString("00000000-0000-0000-0000-000000000060"));
        pag.setStatus(Pagamento.Status.pendente.name());

        when(pagamentos.findByExternalId("ext-pending")).thenReturn(Optional.of(pag));

        service.aplicarEvento("evt-pending", "ext-pending", Pagamento.Status.pendente.name());

        assertThat(pag.getStatus()).isEqualTo(Pagamento.Status.pendente.name());
        verify(outbox, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("receberWebhook publishes PAYMENT_WEBHOOK event")
    void receberWebhook_publishes() {
        when(webhookRecebidoService.receber("pagamento-stripe", "evt-123", "payload"))
                .thenReturn(Optional.of(new br.com.vistoria.core.shared.webhook.WebhookRecebido()));

        service.receberWebhook("stripe", "evt-123", "payload", Map.of("header", "value"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outbox).publish(keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).isEqualTo(EventTypes.PAYMENT_WEBHOOK);
    }

    @Test
    @DisplayName("receberWebhook with missing eventId logs warning")
    void receberWebhook_missingEventId() {
        service.receberWebhook("stripe", null, "payload", Map.of());

        verify(outbox, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("receberWebhook with blank eventId logs warning")
    void receberWebhook_blankEventId() {
        service.receberWebhook("stripe", "  ", "payload", Map.of());

        verify(outbox, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("receberWebhook swallows exception from webhookRecebidoService and does not propagate")
    void receberWebhook_exceptionIsSwallowed() {
        when(webhookRecebidoService.receber("pagamento-stripe", "evt-boom", "payload"))
                .thenThrow(new RuntimeException("db indisponivel"));

        // Should not throw despite the underlying failure.
        service.receberWebhook("stripe", "evt-boom", "payload", Map.of());

        verify(outbox, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("emailDoDestinatario cai no e-mail do cliente quando a imobiliária não tem financeiro")
    void emailDoDestinatario_fallbackCliente() {
        Solicitacao sol = new Solicitacao();
        sol.setId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        sol.setClienteId(UUID.fromString("00000000-0000-0000-0000-000000000050"));

        when(solicitacaoService.get(UUID.fromString("00000000-0000-0000-0000-000000000020"))).thenReturn(sol);
        when(clienteService.emailPorId(UUID.fromString("00000000-0000-0000-0000-000000000050"))).thenReturn(Optional.of("test@example.com"));

        assertThat(service.emailDoDestinatario(UUID.fromString("00000000-0000-0000-0000-000000000020")))
                .isEqualTo("test@example.com");
    }

    private static final UUID SOL_PIX = UUID.fromString("00000000-0000-0000-0000-0000000000a0");

    private Pagamento pagamentoPix(String sufixo, String status) {
        Pagamento p = new Pagamento();
        p.setId(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufixo));
        p.setSolicitacaoId(SOL_PIX);
        p.setValor(new java.math.BigDecimal("270.00"));
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("solicitarPix sem pagamento prévio cria o pagamento e publica jobs.payment.pix-requested")
    void solicitarPix_novo() {
        when(pagamentos.findFirstBySolicitacaoIdOrderByCriadoEmDesc(SOL_PIX)).thenReturn(Optional.empty());
        when(solicitacaoService.totalServicos(SOL_PIX)).thenReturn(new java.math.BigDecimal("270.00"));
        when(pagamentos.saveAndFlush(any(Pagamento.class))).thenAnswer(inv -> {
            Pagamento p = inv.getArgument(0);
            p.setId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
            return p;
        });

        PagamentoService.Cobranca c = service.solicitarPix(SOL_PIX);

        assertThat(c.pixBrCode()).isNull();
        verify(outbox).publish(org.mockito.ArgumentMatchers.eq(br.com.vistoria.core.events.EventTypes.PAYMENT_PIX_REQUESTED),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    @DisplayName("solicitarPix reaproveita o PIX vigente (não republica)")
    void solicitarPix_vigenteReaproveita() {
        Pagamento p = pagamentoPix("a2", Pagamento.Status.pendente.name());
        p.setPixId("pix_1");
        p.setPixBrCode("000201");
        p.setPixExpiraEm(java.time.Instant.now().plusSeconds(600));
        when(pagamentos.findFirstBySolicitacaoIdOrderByCriadoEmDesc(SOL_PIX)).thenReturn(Optional.of(p));

        PagamentoService.Cobranca c = service.solicitarPix(SOL_PIX);

        assertThat(c.pixBrCode()).isEqualTo("000201");
        verify(outbox, org.mockito.Mockito.never()).publish(any(), any());
    }

    @Test
    @DisplayName("solicitarPix com PIX expirado limpa e republica; novo pedido logo em seguida NÃO duplica (marcador em geração)")
    void solicitarPix_expiradoRepublicaUmaVez() {
        Pagamento p = pagamentoPix("a3", Pagamento.Status.pendente.name());
        p.setPixId("pix_velho");
        p.setPixBrCode("000201");
        p.setPixExpiraEm(java.time.Instant.now().minusSeconds(10));
        when(pagamentos.findFirstBySolicitacaoIdOrderByCriadoEmDesc(SOL_PIX)).thenReturn(Optional.of(p));

        service.solicitarPix(SOL_PIX);
        service.solicitarPix(SOL_PIX);

        assertThat(p.getPixBrCode()).isNull();
        assertThat(p.getPixId()).startsWith("gerando-");
        verify(outbox, org.mockito.Mockito.times(1)).publish(any(), any());
    }

    @Test
    @DisplayName("solicitarPix em pagamento já pago devolve a cobrança sem publicar")
    void solicitarPix_jaPago() {
        Pagamento p = pagamentoPix("a4", Pagamento.Status.pago.name());
        when(pagamentos.findFirstBySolicitacaoIdOrderByCriadoEmDesc(SOL_PIX)).thenReturn(Optional.of(p));

        assertThat(service.solicitarPix(SOL_PIX).status()).isEqualTo("pago");
        verify(outbox, org.mockito.Mockito.never()).publish(any(), any());
    }

    @Test
    @DisplayName("registrarPix grava QR/expiração e marca pendente; ignora se já pago")
    void registrarPix_grava() {
        Pagamento p = pagamentoPix("a5", Pagamento.Status.criando.name());
        when(pagamentos.findById(p.getId())).thenReturn(Optional.of(p));
        var expira = java.time.Instant.parse("2026-10-02T12:00:00Z");

        service.registrarPix(p.getId(), "abacatepay", "pix_9", "000201", "data:image/png;base64,AAA", expira);

        assertThat(p.getProvider()).isEqualTo("abacatepay");
        assertThat(p.getPixId()).isEqualTo("pix_9");
        assertThat(p.getPixExpiraEm()).isEqualTo(expira);
        assertThat(p.getStatus()).isEqualTo("pendente");

        p.setStatus(Pagamento.Status.pago.name());
        service.registrarPix(p.getId(), "x", "outro", "x", "x", expira);
        assertThat(p.getPixId()).isEqualTo("pix_9");
    }

    @Test
    @DisplayName("aplicarEvento casa o webhook do PIX pelo pix_id quando external_id não bate")
    void aplicarEvento_porPixId() {
        Pagamento p = pagamentoPix("a6", Pagamento.Status.pendente.name());
        when(pagamentos.findByExternalId("pix_77")).thenReturn(Optional.empty());
        when(pagamentos.findByPixId("pix_77")).thenReturn(Optional.of(p));
        Solicitacao sol = new Solicitacao();
        sol.setId(SOL_PIX);
        when(solicitacaoService.get(SOL_PIX)).thenReturn(sol);

        service.aplicarEvento("evt", "pix_77", "pago");

        assertThat(p.getStatus()).isEqualTo("pago");
        verify(solicitacaoService).aplicarPagamentoConfirmado(SOL_PIX);
    }

    @Test
    @DisplayName("pagadorDe devolve os dados do clientes_b2c; null quando faltam campos")
    void pagadorDe() {
        UUID b2cId = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        Solicitacao sol = new Solicitacao();
        sol.setId(SOL_PIX);
        sol.setClienteB2cId(b2cId);
        when(solicitacaoService.get(SOL_PIX)).thenReturn(sol);
        var c = new br.com.vistoria.core.modules.clienteb2c.ClienteB2c();
        c.setNome("Ana Souza");
        c.setEmail("a@b.com");
        c.setCpf("52998224725");
        c.setTelefone("11999990000");
        when(clientesB2c.findById(b2cId)).thenReturn(Optional.of(c));

        assertThat(service.pagadorDe(SOL_PIX)).isEqualTo(
                new PagamentoService.Pagador("Ana Souza", "a@b.com", "52998224725", "11999990000"));

        c.setTelefone(" ");
        assertThat(service.pagadorDe(SOL_PIX)).isNull();
    }

    @Test
    @DisplayName("emailDoDestinatario B2C: sem imobiliária nem cliente B2B, usa o e-mail do clientes_b2c")
    void emailDoDestinatario_b2c() {
        UUID solId = UUID.fromString("00000000-0000-0000-0000-000000000022");
        UUID b2cId = UUID.fromString("00000000-0000-0000-0000-000000000052");
        Solicitacao sol = new Solicitacao();
        sol.setId(solId);
        sol.setClienteB2cId(b2cId);
        var b2c = new br.com.vistoria.core.modules.clienteb2c.ClienteB2c();
        b2c.setEmail("cliente.b2c@example.com");
        when(solicitacaoService.get(solId)).thenReturn(sol);
        when(clientesB2c.findById(b2cId)).thenReturn(Optional.of(b2c));

        assertThat(service.emailDoDestinatario(solId)).isEqualTo("cliente.b2c@example.com");
    }

    @Test
    @DisplayName("emailDoDestinatario returns null when neither imobiliária nor cliente has email")
    void emailDoDestinatario_noEmail() {
        Solicitacao sol = new Solicitacao();
        sol.setId(UUID.fromString("00000000-0000-0000-0000-000000000021"));
        sol.setClienteId(UUID.fromString("00000000-0000-0000-0000-000000000051"));

        when(solicitacaoService.get(UUID.fromString("00000000-0000-0000-0000-000000000021"))).thenReturn(sol);
        when(clienteService.emailPorId(UUID.fromString("00000000-0000-0000-0000-000000000051"))).thenReturn(Optional.empty());

        assertThat(service.emailDoDestinatario(UUID.fromString("00000000-0000-0000-0000-000000000021"))).isNull();
    }

    @Test
    @DisplayName("aplicarEvento with no cliente email does not publish notify")
    void aplicarEvento_noPagamentoNotification() {
        Pagamento pag = new Pagamento();
        pag.setId(UUID.fromString("00000000-0000-0000-0000-000000000070"));
        pag.setStatus(Pagamento.Status.pendente.name());
        pag.setSolicitacaoId(UUID.fromString("00000000-0000-0000-0000-000000000012"));

        when(pagamentos.findByExternalId("ext-no-email")).thenReturn(Optional.of(pag));

        Solicitacao sol = new Solicitacao();
        sol.setId(UUID.fromString("00000000-0000-0000-0000-000000000012"));
        sol.setClienteId(UUID.fromString("00000000-0000-0000-0000-000000000052"));

        when(solicitacaoService.get(UUID.fromString("00000000-0000-0000-0000-000000000012"))).thenReturn(sol);
        when(clienteService.emailPorId(UUID.fromString("00000000-0000-0000-0000-000000000052"))).thenReturn(Optional.empty());

        service.aplicarEvento("evt-no-email", "ext-no-email", Pagamento.Status.pago.name());

        // Sem e-mail de destinatário -> nenhuma notificação publicada.
        verify(outbox, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("receberWebhook with empty headers handles correctly")
    void receberWebhook_emptyHeaders() {
        when(webhookRecebidoService.receber("pagamento-stripe", "evt-456", "payload"))
                .thenReturn(Optional.of(new br.com.vistoria.core.shared.webhook.WebhookRecebido()));

        service.receberWebhook("stripe", "evt-456", "payload", null);

        ArgumentCaptor<Map> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(anyString(), eventCaptor.capture());

        Map event = eventCaptor.getValue();
        assertThat(event).containsKey("headers");
    }
}
