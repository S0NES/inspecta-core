package br.com.vistoria.core.modules.imobiliaria;

import br.com.vistoria.core.config.TenantContext;
import br.com.vistoria.core.modules.imobiliaria.dto.CreateImobiliariaRequest;
import br.com.vistoria.core.modules.imobiliaria.dto.ImobiliariaResponse;
import br.com.vistoria.core.modules.integracao.IntegracaoSyncService;
import br.com.vistoria.core.shared.exception.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImobiliariaService")
class ImobiliariaServiceTest {

    @Mock
    private ImobiliariaRepository repository;

    @Mock
    private IntegracaoSyncService integracaoSync;

    @Mock
    private br.com.vistoria.core.shared.outbox.OutboxService outbox;

    @Mock
    private br.com.vistoria.core.modules.contrato.ContratoRepositories.ContratoRepository contratos;

    @Mock
    private br.com.vistoria.core.modules.contrato.ContratoRepositories.ContratoServicoGrupoRepository grupos;

    @Mock
    private br.com.vistoria.core.modules.contrato.ContratoRepositories.FaixaRepository faixas;

    @Mock
    private br.com.vistoria.core.modules.cadastro.CadastroRepositories.ServicoRepository servicos;

    private ImobiliariaService service;

    @BeforeEach
    void setUp() {
        service = new ImobiliariaService(repository, integracaoSync, outbox, contratos, grupos, faixas, servicos);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("list returns all agencies when tenant is not set (ADMIN/SUPORTE)")
    void list_noTenant_returnsAll() {
        TenantContext.clear();
        Pageable pageable = Pageable.unpaged();

        Imobiliaria a = new Imobiliaria();
        a.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Imobiliaria b = new Imobiliaria();
        b.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        Page<Imobiliaria> page = new PageImpl<>(List.of(a, b));

        when(repository.findAll(pageable)).thenReturn(page);

        Page<ImobiliariaResponse> result = service.list(pageable);

        assertThat(result.getContent()).hasSize(2);
        verify(repository).findAll(pageable);
    }

    @Test
    @DisplayName("list returns only own agency when tenant is set (IMOB_GERENCIA)")
    void list_withTenant_returnsOnlyOwn() {
        TenantContext.set(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        Pageable pageable = Pageable.unpaged();

        Imobiliaria own = new Imobiliaria();
        own.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        Page<Imobiliaria> page = new PageImpl<>(List.of(own));

        when(repository.findAllByIdIn(eq(List.of(UUID.fromString("00000000-0000-0000-0000-000000000005"))), eq(pageable))).thenReturn(page);

        Page<ImobiliariaResponse> result = service.list(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        verify(repository).findAllByIdIn(eq(List.of(UUID.fromString("00000000-0000-0000-0000-000000000005"))), eq(pageable));
    }

    @Test
    @DisplayName("create saves new imobiliaria with valid CNPJ")
    void create_savesImobiliaria() {
        CreateImobiliariaRequest req = new CreateImobiliariaRequest(
            "Imobiliaria Exemplo LTDA",
            "Imobiliaria Exemplo",
            "11.222.333/0001-81",
            "chave.pix@imobiliaria.com",
            "financeiro@imobiliaria.com",
            "11 98765-4321"
        );

        Imobiliaria saved = new Imobiliaria();
        saved.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        saved.setCnpj("11.222.333/0001-81");
        saved.setRazaoSocial("Imobiliaria Exemplo LTDA");
        saved.setNomeFantasia("Imobiliaria Exemplo");
        saved.setPix("chave.pix@imobiliaria.com");
        saved.setEmailFinanceiro("financeiro@imobiliaria.com");
        saved.setTelefone("11 98765-4321");

        when(repository.existsByCnpj("11222333000181")).thenReturn(false);
        when(repository.save(any(Imobiliaria.class))).thenReturn(saved);

        ImobiliariaResponse result = service.create(req);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(result.cnpj()).isEqualTo("11.222.333/0001-81");
        verify(repository).save(any(Imobiliaria.class));
    }

    @Test
    @DisplayName("create throws when CNPJ already exists")
    void create_throwsWhenCnpjExists() {
        CreateImobiliariaRequest req = new CreateImobiliariaRequest(
            "Imobiliaria Exemplo LTDA",
            "Imobiliaria Exemplo",
            "11.222.333/0001-81",
            "chave.pix@imobiliaria.com",
            "financeiro@imobiliaria.com",
            "11 98765-4321"
        );

        when(repository.existsByCnpj("11222333000181")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(br.com.vistoria.core.shared.exception.CnpjDuplicadoException.class)
            .hasMessageContaining("CNPJ ja cadastrado");
    }

    @Test
    @DisplayName("findById returns imobiliaria when exists")
    void findById_returnsImobiliaria() {
        Imobiliaria imobiliaria = new Imobiliaria();
        imobiliaria.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        imobiliaria.setCnpj("11.222.333/0001-81");
        imobiliaria.setRazaoSocial("Imobiliaria Exemplo LTDA");

        when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(imobiliaria));

        ImobiliariaResponse result = service.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(result.cnpj()).isEqualTo("11.222.333/0001-81");
    }

    @Test
    @DisplayName("findById throws NotFoundException when not found")
    void findById_throwsNotFound() {
        when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000999"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(UUID.fromString("00000000-0000-0000-0000-000000000999")))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Imobiliaria 00000000-0000-0000-0000-000000000999");
    }

    @Test
    @DisplayName("create maps all fields to saved entity")
    void create_mapsAllFields() {
        CreateImobiliariaRequest req = new CreateImobiliariaRequest(
            "Razao Social Test",
            "Nome Fantasia Test",
            "11.222.333/0001-81",
            "pix@test.com",
            "financeiro@test.com",
            "21 99999-8888"
        );

        Imobiliaria saved = new Imobiliaria();
        saved.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        saved.setCnpj("11.222.333/0001-81");
        saved.setRazaoSocial("Razao Social Test");
        saved.setNomeFantasia("Nome Fantasia Test");
        saved.setPix("pix@test.com");
        saved.setEmailFinanceiro("financeiro@test.com");
        saved.setTelefone("21 99999-8888");
        saved.setAtiva(true);

        when(repository.existsByCnpj("11222333000181")).thenReturn(false);
        when(repository.save(any(Imobiliaria.class))).thenReturn(saved);

        ImobiliariaResponse result = service.create(req);

        assertThat(result.id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(result.cnpj()).isEqualTo("11.222.333/0001-81");
        assertThat(result.razaoSocial()).isEqualTo("Razao Social Test");
        assertThat(result.nomeFantasia()).isEqualTo("Nome Fantasia Test");
        assertThat(result.ativa()).isTrue();
    }

    @Test
    @DisplayName("create publica jobs.payment.customer-create com o CNPJ só dígitos")
    void create_publicaCustomerCreate() {
        CreateImobiliariaRequest req = new CreateImobiliariaRequest(
            "Razao X", "Fantasia X", "11.222.333/0001-81", "pix@x.com", "fin@x.com", "21 99999-8888");
        Imobiliaria saved = new Imobiliaria();
        saved.setId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
        saved.setCnpj("11.222.333/0001-81");
        saved.setRazaoSocial("Razao X");
        saved.setEmailFinanceiro("fin@x.com");
        saved.setTelefone("21 99999-8888");
        when(repository.existsByCnpj("11222333000181")).thenReturn(false);
        when(repository.save(any(Imobiliaria.class))).thenReturn(saved);

        service.create(req);

        org.mockito.ArgumentCaptor<java.util.Map> ev = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(outbox).publish(eq(br.com.vistoria.core.events.EventTypes.PAYMENT_CUSTOMER_CREATE), ev.capture());
        assertThat(ev.getValue()).containsEntry("taxId", "11222333000181");
        assertThat(ev.getValue()).containsEntry("imobiliariaId", saved.getId());
    }

    @Test
    @DisplayName("delete publica jobs.payment.customer-delete quando há external_payment_id e remove a linha")
    void delete_publicaCustomerDelete() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        Imobiliaria i = new Imobiliaria();
        i.setId(id);
        i.setExternalPaymentId("cust_123");
        when(repository.findById(id)).thenReturn(Optional.of(i));

        service.delete(id);

        verify(outbox).publish(eq(br.com.vistoria.core.events.EventTypes.PAYMENT_CUSTOMER_DELETE), any());
        verify(repository).delete(i);
    }

    @Test
    @DisplayName("delete sem external_payment_id não publica evento mas remove a linha")
    void delete_semCustomer() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
        Imobiliaria i = new Imobiliaria();
        i.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(i));

        service.delete(id);

        verify(outbox, org.mockito.Mockito.never()).publish(any(), any());
        verify(repository).delete(i);
    }

    @Test
    @DisplayName("create persiste o contrato de biletagem quando o body traz contrato")
    void create_persistsContratoWhenProvided() {
        UUID servicoId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        var contrato = new CreateImobiliariaRequest.ContratoCreateRequest(
                "faixa", null, null,
                List.of(new CreateImobiliariaRequest.GrupoItem(servicoId, null, null)),
                List.of(new CreateImobiliariaRequest.FaixaItem(
                        servicoId, (short) 1,
                        new java.math.BigDecimal("100"), new java.math.BigDecimal("120"),
                        null, null)));
        CreateImobiliariaRequest req = new CreateImobiliariaRequest(
                "Razao X", "Fantasia X", "11.222.333/0001-81", null, "fin@x.com", "11999998888",
                null, null, null, null, null, null, contrato);

        Imobiliaria saved = new Imobiliaria();
        saved.setId(UUID.fromString("00000000-0000-0000-0000-0000000000c2"));
        saved.setCnpj("11.222.333/0001-81");
        saved.setRazaoSocial("Razao X");
        when(repository.existsByCnpj("11222333000181")).thenReturn(false);
        when(repository.save(any(Imobiliaria.class))).thenReturn(saved);
        when(contratos.save(any())).thenAnswer(inv -> {
            br.com.vistoria.core.modules.contrato.Contrato c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.fromString("00000000-0000-0000-0000-000000000077"));
            return c;
        });
        when(servicos.existsById(servicoId)).thenReturn(true);

        service.create(req);

        org.mockito.ArgumentCaptor<br.com.vistoria.core.modules.contrato.Contrato> cap =
                org.mockito.ArgumentCaptor.forClass(br.com.vistoria.core.modules.contrato.Contrato.class);
        verify(contratos).save(cap.capture());
        assertThat(cap.getValue().getModo()).isEqualTo(br.com.vistoria.core.modules.contrato.Contrato.Modo.faixa);
        assertThat(cap.getValue().getImobiliariaId()).isEqualTo(saved.getId());
        verify(grupos).save(any());
        verify(faixas).save(any());
    }

    @Test
    @DisplayName("create sem contrato no body não grava biletagem")
    void create_withoutContrato_skipsBiletagem() {
        CreateImobiliariaRequest req = new CreateImobiliariaRequest(
            "Razao X", "Fantasia X", "11.222.333/0001-81", "pix@x.com", "fin@x.com", "21 99999-8888");
        Imobiliaria saved = new Imobiliaria();
        saved.setId(UUID.fromString("00000000-0000-0000-0000-0000000000c3"));
        saved.setCnpj("11.222.333/0001-81");
        when(repository.existsByCnpj("11222333000181")).thenReturn(false);
        when(repository.save(any(Imobiliaria.class))).thenReturn(saved);

        service.create(req);

        verify(contratos, org.mockito.Mockito.never()).save(any());
    }
}
