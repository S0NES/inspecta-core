package br.com.vistoria.core.modules.solicitacao;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories;
import br.com.vistoria.core.modules.cadastro.Cliente;
import br.com.vistoria.core.modules.cadastro.Imovel;
import br.com.vistoria.core.modules.contrato.Contrato;
import br.com.vistoria.core.modules.contrato.PrecoService;
import br.com.vistoria.core.modules.financeiro.ExtratoItem;
import br.com.vistoria.core.modules.financeiro.FinanceiroRepositories.ExtratoItemRepository;
import br.com.vistoria.core.modules.integracao.IntegracaoSyncService;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoRepositories.*;
import br.com.vistoria.core.modules.solicitacao.dto.CreateSolicitacaoRequest;
import br.com.vistoria.core.modules.solicitacao.dto.CreateSolicitacaoRequest.ServicoItem;
import br.com.vistoria.core.modules.solicitacao.dto.CreateSolicitacaoRequest.SolicitacaoIntervenienteItem;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SolicitacaoService")
class SolicitacaoServiceTest {

    @Mock private SolicitacaoRepository solicitacoes;
    @Mock private ServicoRepository servicos;
    @Mock private SolicitacaoIntervenienteRepository intervenientes;
    @Mock private CadastroRepositories.ImovelRepository imoveis;
    @Mock private CadastroRepositories.ClienteRepository clientes;
    @Mock private CadastroRepositories.IntervenientePapelRepository papeis;
    @Mock private PrecoService precoService;
    @Mock private OutboxService outbox;
    @Mock private IntegracaoSyncService integracaoSync;
    @Mock private ExtratoItemRepository extratoItens;

    private SolicitacaoService service;

    private static final UUID IMOB_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID IMOB_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID IMOVEL = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID CLIENTE = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID TIPO = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @BeforeEach
    void setUp() {
        service = new SolicitacaoService(solicitacoes, servicos, intervenientes, imoveis, clientes,
                papeis, precoService, outbox, integracaoSync, extratoItens);
    }


    @Test @DisplayName("get returns solicitacao when exists")
    void get_returnsSolicitacao() {
        Solicitacao sol = new Solicitacao();
        sol.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(solicitacoes.findById(sol.getId())).thenReturn(Optional.of(sol));
        assertThat(service.get(sol.getId())).isEqualTo(sol);
    }

    @Test @DisplayName("get throws NotFoundException when not found")
    void get_throwsNotFound() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000999");
        when(solicitacoes.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
    }

    @Test @DisplayName("totalServicos sums preco snapshot")
    void totalServicos_sums() {
        SolicitacaoServico s1 = new SolicitacaoServico(); s1.setPrecoSnapshot(new BigDecimal("100.00"));
        SolicitacaoServico s2 = new SolicitacaoServico(); s2.setPrecoSnapshot(new BigDecimal("50.00"));
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(servicos.findBySolicitacaoId(id)).thenReturn(List.of(s1, s2));
        assertThat(service.totalServicos(id)).isEqualByComparingTo("150.00");
    }

    @Test @DisplayName("statusDerivado returns vazia when no servicos")
    void statusDerivado_empty() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(servicos.findBySolicitacaoId(id)).thenReturn(List.of());
        assertThat(service.statusDerivado(id)).isEqualTo("vazia");
    }

    @Test @DisplayName("cancelarServico marks servico as cancelada")
    void cancelarServico_cancels() {
        SolicitacaoServico ss = new SolicitacaoServico();
        ss.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        ss.setStatus(SolicitacaoServico.Status.pendente);
        when(servicos.findById(ss.getId())).thenReturn(Optional.of(ss));
        service.cancelarServico(ss.getId());
        assertThat(ss.getStatus()).isEqualTo(SolicitacaoServico.Status.cancelada);
    }

    @Test @DisplayName("cancelarServico throws when em_execucao")
    void cancelarServico_throwsWhenEmExecucao() {
        SolicitacaoServico ss = new SolicitacaoServico();
        ss.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        ss.setStatus(SolicitacaoServico.Status.em_execucao);
        when(servicos.findById(ss.getId())).thenReturn(Optional.of(ss));
        assertThatThrownBy(() -> service.cancelarServico(ss.getId()))
                .isInstanceOf(IllegalStateException.class);
    }


    private CreateSolicitacaoRequest req(SolicitacaoIntervenienteItem... pessoas) {
        return reqFull(CLIENTE, null, null, pessoas);
    }

    private CreateSolicitacaoRequest reqFull(UUID clienteId, BigDecimal valorAluguel, Boolean mobiliado,
                                             SolicitacaoIntervenienteItem... pessoas) {
        return new CreateSolicitacaoRequest(clienteId, IMOVEL, false, null, null,
                valorAluguel, mobiliado,
                Solicitacao.Origem.painel,
                List.of(new ServicoItem(TIPO, null, Instant.parse("2026-10-01T13:00:00Z"))),
                List.of(pessoas), null);
    }

    private SolicitacaoIntervenienteItem locador() {
        return new SolicitacaoIntervenienteItem("locador", "Ana Silva", "111.444.777-35", "ana@x.co");
    }

    private Imovel imovel(UUID imobId) {
        Imovel i = new Imovel();
        i.setId(IMOVEL);
        i.setImobiliariaId(imobId);
        i.setMetragem(new BigDecimal("80"));
        return i;
    }

    private Cliente cliente(UUID imobId) {
        Cliente c = new Cliente();
        c.setId(CLIENTE);
        c.setNome("Fulano");
        c.setImobiliariaId(imobId);
        return c;
    }

    private Contrato contrato(Contrato.Modo modo) {
        Contrato c = new Contrato();
        c.setModo(modo);
        return c;
    }

    private void stubSave() {
        when(solicitacoes.saveAndFlush(any(Solicitacao.class))).thenAnswer(inv -> {
            Solicitacao s = inv.getArgument(0);
            s.setId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));
            return s;
        });
    }

    /** Espelha o @UuidGenerator: o id do serviço só existe após o save (usado no publish B2B). */
    private void stubServicoSave() {
        when(servicos.save(any(SolicitacaoServico.class))).thenAnswer(inv -> {
            SolicitacaoServico ss = inv.getArgument(0);
            if (ss.getId() == null) ss.setId(UUID.randomUUID());
            return ss;
        });
    }

    @Test
    @DisplayName("criar — cliente da imob B + imóvel da imob A falha")
    void criar_clienteImobiliariaDivergente() {
        when(imoveis.findById(IMOVEL)).thenReturn(Optional.of(imovel(IMOB_A)));
        when(clientes.findById(CLIENTE)).thenReturn(Optional.of(cliente(IMOB_B)));

        assertThatThrownBy(() -> service.criar(req(locador()), USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cliente_imobiliaria_divergente");
        verify(solicitacoes, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("criar — imóvel sem imobiliária NÃO lança (porta B2C) e não atribui nem cria extrato")
    void criar_imovelSemImobiliaria_naoLanca() {
        when(imoveis.findById(IMOVEL)).thenReturn(Optional.of(imovel(null)));
        when(papeis.existsBySlugAndAtivoTrue("locador")).thenReturn(true);
        when(precoService.resolver(any(), eq(TIPO), any(), any(), any()))
                .thenReturn(new PrecoService.Preco(new BigDecimal("100.00"), null, null));
        stubSave();

        Solicitacao s = service.criar(reqFull(null, null, null, locador()), USER);

        assertThat(s.getImobiliariaId()).isNull();
        assertThat(s.getClienteId()).isNull();
        verify(outbox, never()).publish(eq(EventTypes.ASSIGNMENT_REQUESTED), any());
        verify(extratoItens, never()).save(any());
        verify(integracaoSync).enfileirar("solicitacao", s.getId());
    }

    @Test
    @DisplayName("criar — sem clienteId cria normalmente e nem consulta Cliente")
    void criar_semClienteId_ok() {
        when(imoveis.findById(IMOVEL)).thenReturn(Optional.of(imovel(IMOB_A)));
        when(papeis.existsBySlugAndAtivoTrue("locador")).thenReturn(true);
        when(precoService.resolver(eq(IMOB_A), eq(TIPO), any(), any(), any()))
                .thenReturn(new PrecoService.Preco(new BigDecimal("300.00"), null, null));
        stubSave();
        stubServicoSave();

        Solicitacao s = service.criar(reqFull(null, null, null, locador()), USER);

        assertThat(s.getClienteId()).isNull();
        verify(clientes, never()).findById(any());
        verify(outbox, times(1)).publish(eq(EventTypes.ASSIGNMENT_REQUESTED), any());
        verify(extratoItens, times(1)).save(any(ExtratoItem.class));
    }

    @Test
    @DisplayName("criar — CPF inválido no interveniente → 'cpf_invalido', nada persiste")
    void criar_cpfInvalido() {
        when(imoveis.findById(IMOVEL)).thenReturn(Optional.of(imovel(IMOB_A)));
        when(clientes.findById(CLIENTE)).thenReturn(Optional.of(cliente(IMOB_A)));
        when(papeis.existsBySlugAndAtivoTrue("locador")).thenReturn(true);

        var pessoa = new SolicitacaoIntervenienteItem("locador", "Ana Silva", "111.444.777-34", "ana@x.co");
        assertThatThrownBy(() -> service.criar(req(pessoa), USER))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("cpf_invalido");
        verify(solicitacoes, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("criar — papel não ativo → 'papel_invalido'")
    void criar_papelInvalido() {
        when(imoveis.findById(IMOVEL)).thenReturn(Optional.of(imovel(IMOB_A)));
        when(clientes.findById(CLIENTE)).thenReturn(Optional.of(cliente(IMOB_A)));
        when(papeis.existsBySlugAndAtivoTrue("zelador")).thenReturn(false);

        var pessoa = new SolicitacaoIntervenienteItem("zelador", "Ana Silva", "111.444.777-35", "ana@x.co");
        assertThatThrownBy(() -> service.criar(req(pessoa), USER))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("papel_invalido");
    }

    @Test
    @DisplayName("criar — contrato percentual e sem valorAluguel → erro (422 no inspecta-api)")
    void criar_percentualSemAluguel_erro() {
        when(imoveis.findById(IMOVEL)).thenReturn(Optional.of(imovel(IMOB_A)));
        when(precoService.contratoAtivo(IMOB_A)).thenReturn(Optional.of(contrato(Contrato.Modo.percentual)));

        assertThatThrownBy(() -> service.criar(reqFull(null, null, null, locador()), USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("aluguel_e_mobiliado_obrigatorios_no_modo_percentual");
        verify(solicitacoes, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("criar — sem contrato/serviço configurado → ContratoNaoConfiguradoException (422 no inspecta-api)")
    void criar_semContrato() {
        when(imoveis.findById(IMOVEL)).thenReturn(Optional.of(imovel(IMOB_A)));
        when(clientes.findById(CLIENTE)).thenReturn(Optional.of(cliente(IMOB_A)));
        when(papeis.existsBySlugAndAtivoTrue("locador")).thenReturn(true);
        stubSave();
        when(precoService.resolver(eq(IMOB_A), eq(TIPO), any(), any(), any()))
                .thenThrow(new NotFoundException("Contrato ativo nao encontrado"));

        assertThatThrownBy(() -> service.criar(req(locador()), USER))
                .isInstanceOf(br.com.vistoria.core.modules.contrato.ContratoNaoConfiguradoException.class);
    }

    @Test
    @DisplayName("criar B2B — publica ASSIGNMENT_REQUESTED na criação e cria ExtratoItem em aberto")
    void criar_b2b_publicaAssignmentECriaExtratoItem() {
        when(imoveis.findById(IMOVEL)).thenReturn(Optional.of(imovel(IMOB_A)));
        when(clientes.findById(CLIENTE)).thenReturn(Optional.of(cliente(IMOB_A)));
        when(papeis.existsBySlugAndAtivoTrue("locador")).thenReturn(true);
        when(precoService.resolver(eq(IMOB_A), eq(TIPO), any(), any(), any()))
                .thenReturn(new PrecoService.Preco(new BigDecimal("650.00"), new BigDecimal("400.00"), null));
        stubSave();
        stubServicoSave();

        Solicitacao s = service.criar(req(locador()), USER);

        assertThat(s.getImobiliariaId()).isEqualTo(IMOB_A);
        assertThat(s.getClienteId()).isEqualTo(CLIENTE);

        ArgumentCaptor<SolicitacaoServico> ss = ArgumentCaptor.forClass(SolicitacaoServico.class);
        verify(servicos).save(ss.capture());
        assertThat(ss.getValue().getPrecoSnapshot()).isEqualByComparingTo("650.00");
        assertThat(ss.getValue().getProductId()).isNotNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> ev = ArgumentCaptor.forClass(Map.class);
        verify(outbox, times(1)).publish(eq(EventTypes.ASSIGNMENT_REQUESTED), ev.capture());
        assertThat(ev.getValue()).containsEntry("servicoId", ss.getValue().getId());
        assertThat(ev.getValue().get("eventId")).isEqualTo("assign-pay-" + ss.getValue().getId());

        ArgumentCaptor<ExtratoItem> ei = ArgumentCaptor.forClass(ExtratoItem.class);
        verify(extratoItens).save(ei.capture());
        assertThat(ei.getValue().getImobiliariaId()).isEqualTo(IMOB_A);
        assertThat(ei.getValue().getValor()).isEqualByComparingTo("650.00");
        assertThat(ei.getValue().getFaturaId()).isNull();

        verify(outbox, never()).publish(eq(EventTypes.PAYMENT_CHARGE_REQUESTED), any());
        verify(integracaoSync).enfileirar("solicitacao", s.getId());
    }

    @Test
    @DisplayName("criar — PrecoService recebe mobiliado/valorAluguel da request (não do imóvel)")
    void criar_precoServiceRecebeCamposDaRequest() {
        when(imoveis.findById(IMOVEL)).thenReturn(Optional.of(imovel(IMOB_A)));
        when(clientes.findById(CLIENTE)).thenReturn(Optional.of(cliente(IMOB_A)));
        when(papeis.existsBySlugAndAtivoTrue("locador")).thenReturn(true);
        when(precoService.contratoAtivo(IMOB_A)).thenReturn(Optional.of(contrato(Contrato.Modo.percentual)));
        when(precoService.resolver(eq(IMOB_A), eq(TIPO), eq(new BigDecimal("80")),
                eq(Boolean.TRUE), eq(new BigDecimal("2500.00"))))
                .thenReturn(new PrecoService.Preco(new BigDecimal("250.00"), null, null));
        stubSave();
        stubServicoSave();

        service.criar(reqFull(CLIENTE, new BigDecimal("2500.00"), true, locador()), USER);

        verify(precoService).resolver(eq(IMOB_A), eq(TIPO), eq(new BigDecimal("80")),
                eq(Boolean.TRUE), eq(new BigDecimal("2500.00")));
    }

    @Test
    @DisplayName("aplicarPagamentoConfirmado — marca payment_status=pago e publica 1 assignment por serviço pendente")
    void aplicarPagamentoConfirmado_publicaAtribuicoes() {
        UUID solId = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        Solicitacao sol = new Solicitacao();
        sol.setId(solId);
        when(solicitacoes.findById(solId)).thenReturn(Optional.of(sol));

        SolicitacaoServico pend = new SolicitacaoServico();
        pend.setId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
        pend.setStatus(SolicitacaoServico.Status.pendente);
        SolicitacaoServico jaAtribuida = new SolicitacaoServico();
        jaAtribuida.setId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
        jaAtribuida.setStatus(SolicitacaoServico.Status.atribuida);
        when(servicos.findBySolicitacaoId(solId)).thenReturn(List.of(pend, jaAtribuida));

        int n = service.aplicarPagamentoConfirmado(solId);

        assertThat(n).isEqualTo(1);
        assertThat(sol.getPaymentStatus()).isEqualTo("pago");
        verify(outbox, times(1)).publish(eq(EventTypes.ASSIGNMENT_REQUESTED), any());
    }
}
