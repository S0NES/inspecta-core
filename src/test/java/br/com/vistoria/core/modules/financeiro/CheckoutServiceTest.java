package br.com.vistoria.core.modules.financeiro;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories;
import br.com.vistoria.core.modules.cadastro.Servico;
import br.com.vistoria.core.modules.cadastro.ServicoTipo;
import br.com.vistoria.core.modules.cadastro.TipoServico;
import br.com.vistoria.core.modules.imobiliaria.Imobiliaria;
import br.com.vistoria.core.modules.imobiliaria.ImobiliariaRepository;
import br.com.vistoria.core.modules.solicitacao.Solicitacao;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoServico;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoRepositories;
import br.com.vistoria.core.shared.outbox.OutboxService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckoutService")
class CheckoutServiceTest {

    @Mock private SolicitacaoRepositories.SolicitacaoRepository solicitacoes;
    @Mock private SolicitacaoRepositories.ServicoRepository servicos;
    @Mock private CadastroRepositories.TipoServicoRepository tiposServico;
    @Mock private CadastroRepositories.ServicoRepository servicosCatalogo;
    @Mock private CadastroRepositories.ServicoTipoRepository servicoTipos;
    @Mock private ImobiliariaRepository imobiliarias;
    @Mock private br.com.vistoria.core.modules.b2cservico.B2cServiceRepository b2cServicos;
    @Mock private OutboxService outbox;

    private CheckoutService service;

    private static final UUID SOL = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID IMOB = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID TIPO = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final UUID SERVICO = UUID.fromString("00000000-0000-0000-0000-000000000031");
    private static final UUID SS = UUID.fromString("00000000-0000-0000-0000-000000000041");

    @BeforeEach
    void setUp() {
        service = new CheckoutService(solicitacoes, servicos, tiposServico, servicosCatalogo,
                servicoTipos, imobiliarias, b2cServicos, outbox);
    }

    private SolicitacaoServico servico(String extProdId) {
        SolicitacaoServico ss = new SolicitacaoServico();
        ss.setId(SS);
        ss.setSolicitacaoId(SOL);
        ss.setTipoServicoId(TIPO);
        ss.setStatus(SolicitacaoServico.Status.pendente);
        ss.setProductId(UUID.fromString("00000000-0000-0000-0000-000000000051"));
        ss.setPrecoSnapshot(new BigDecimal("650.00"));
        ss.setExternalProductId(extProdId);
        return ss;
    }

    @Test
    @DisplayName("planejar — productName = tipo - servico, preço em centavos, customerId da imobiliária")
    void planejar_montaItens() {
        Solicitacao sol = new Solicitacao();
        sol.setId(SOL);
        sol.setImobiliariaId(IMOB);
        when(solicitacoes.findById(SOL)).thenReturn(Optional.of(sol));

        Imobiliaria imob = new Imobiliaria();
        imob.setId(IMOB);
        imob.setExternalPaymentId("cust_9");
        when(imobiliarias.findById(IMOB)).thenReturn(Optional.of(imob));

        when(servicos.findBySolicitacaoId(SOL)).thenReturn(List.of(servico(null)));

        TipoServico tipo = new TipoServico();
        tipo.setNome("Vistoria de entrada");
        when(tiposServico.findById(TIPO)).thenReturn(Optional.of(tipo));

        ServicoTipo st = new ServicoTipo();
        st.setServicoId(SERVICO);
        when(servicoTipos.findByTipoServicoId(TIPO)).thenReturn(List.of(st));
        Servico servicoGuardaChuva = new Servico();
        servicoGuardaChuva.setNome("Elegance");
        when(servicosCatalogo.findById(SERVICO)).thenReturn(Optional.of(servicoGuardaChuva));

        CheckoutService.PlanoCheckout plano = service.planejar(SOL);

        assertThat(plano.customerId()).isEqualTo("cust_9");
        assertThat(plano.itens()).hasSize(1);
        CheckoutService.ItemCheckout item = plano.itens().get(0);
        assertThat(item.productName()).isEqualTo("Vistoria de entrada - Elegance");
        assertThat(item.precoCentavos()).isEqualTo(65000L);
        assertThat(item.externalProductId()).isNull();
    }

    @Test
    @DisplayName("planejar B2C — productName = nome do b2c_service (Biletagem Cliente), sem Serviço guarda-chuva do B2B")
    void planejar_b2cUsaNomeDoServicoB2c() {
        UUID b2c = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        Solicitacao sol = new Solicitacao();
        sol.setId(SOL);
        when(solicitacoes.findById(SOL)).thenReturn(Optional.of(sol));
        SolicitacaoServico ss = servico(null);
        ss.setB2cServiceId(b2c);
        when(servicos.findBySolicitacaoId(SOL)).thenReturn(List.of(ss));
        var b2cServico = new br.com.vistoria.core.modules.b2cservico.B2cService();
        b2cServico.setNome("Vistoria Express");
        when(b2cServicos.findById(b2c)).thenReturn(Optional.of(b2cServico));

        CheckoutService.PlanoCheckout plano = service.planejar(SOL);

        assertThat(plano.customerId()).isNull();
        assertThat(plano.itens().get(0).productName()).isEqualTo("Vistoria Express");
    }

    @Test
    @DisplayName("registrarCheckoutPronto — grava ids, payment_status=checkout_ready, publica evento")
    void registrarCheckoutPronto_publica() {
        Solicitacao sol = new Solicitacao();
        sol.setId(SOL);
        when(solicitacoes.findById(SOL)).thenReturn(Optional.of(sol));

        service.registrarCheckoutPronto(SOL, "chk_1", "https://pay/chk_1");

        assertThat(sol.getCheckoutId()).isEqualTo("chk_1");
        assertThat(sol.getCheckoutUrl()).isEqualTo("https://pay/chk_1");
        assertThat(sol.getPaymentStatus()).isEqualTo("checkout_ready");
        verify(outbox).publish(eq(EventTypes.PAYMENT_CHECKOUT_READY), any());
    }

    @Test
    @DisplayName("registrarProdutoExterno — grava external_product_id no serviço")
    void registrarProdutoExterno_grava() {
        SolicitacaoServico ss = servico(null);
        when(servicos.findById(SS)).thenReturn(Optional.of(ss));

        service.registrarProdutoExterno(SS, "prod_abc");

        assertThat(ss.getExternalProductId()).isEqualTo("prod_abc");
    }

    @Test
    @DisplayName("marcarErro — só marca 'erro' se ainda não estava pronto/pago")
    void marcarErro_naoSobrescrevePronto() {
        Solicitacao sol = new Solicitacao();
        sol.setId(SOL);
        sol.setPaymentStatus("checkout_ready");
        when(solicitacoes.findById(SOL)).thenReturn(Optional.of(sol));

        service.marcarErro(SOL);

        assertThat(sol.getPaymentStatus()).isEqualTo("checkout_ready");
    }
}
