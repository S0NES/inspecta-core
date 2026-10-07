package br.com.vistoria.core.modules.contrato;

import br.com.vistoria.core.modules.cadastro.FaixaReferencia;
import br.com.vistoria.core.modules.cadastro.ServicoTipo;
import br.com.vistoria.core.shared.exception.NotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrecoService")
class PrecoServiceTest {

    private static final UUID SERVICO_ENTRADA = UUID.fromString("00000000-b2c2-0000-0000-000000000001");
    private static final UUID SERVICO_SAIDA = UUID.fromString("00000000-b2c2-0000-0000-000000000002");

    @Mock
    private ContratoRepositories.ContratoRepository contratos;

    @Mock
    private ContratoRepositories.FaixaRepository faixas;

    @Mock
    private ContratoRepositories.RepasseRepository repasses;

    @Mock
    private ContratoRepositories.ServicoRepository servicos;

    @Mock
    private ContratoRepositories.ContratoServicoGrupoRepository grupos;

    @Mock
    private br.com.vistoria.core.modules.cadastro.CadastroRepositories.ServicoTipoRepository servicoTipos;

    @Mock
    private br.com.vistoria.core.modules.cadastro.CadastroRepositories.FaixaReferenciaRepository faixaReferencia;

    @Mock
    private br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteRepositories.BiletagemClienteRepository biletagemCliente;

    @Mock
    private br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteRepositories.BiletagemClientePercentualRepository biletagemClientePercentual;

    @Mock
    private br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteRepositories.BiletagemClienteFaixaRepository biletagemClienteFaixa;

    private PrecoService service;

    @BeforeEach
    void setUp() {
        service = new PrecoService(contratos, faixas, repasses, servicos, grupos, servicoTipos, faixaReferencia,
                biletagemCliente, biletagemClientePercentual, biletagemClienteFaixa);
    }


    private static final UUID CONTRATO_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TIPO_SERVICO_ID = UUID.fromString("00000000-0000-0000-0000-000000000200");
    private static final UUID SERVICO_ID = UUID.fromString("00000000-0000-0000-0000-000000000900");

    private Contrato contratoFaixa() {
        Contrato c = new Contrato();
        c.setId(CONTRATO_ID);
        c.setModo(Contrato.Modo.faixa);
        return c;
    }

    private void stubServicoAtivo() {
        ContratoServico svc = new ContratoServico();
        svc.setAtivo(true);
        when(servicos.findFirstByContratoIdAndTipoServicoId(CONTRATO_ID, TIPO_SERVICO_ID))
                .thenReturn(Optional.of(svc));
    }

    private ServicoTipo servicoTipo() {
        ServicoTipo st = new ServicoTipo();
        st.setServicoId(SERVICO_ID);
        st.setTipoServicoId(TIPO_SERVICO_ID);
        return st;
    }

    private FaixaReferencia band(int id, String min, String max) {
        FaixaReferencia fr = new FaixaReferencia();
        fr.setId((short) id);
        fr.setOrdem((short) id);
        fr.setMetragemMin(new BigDecimal(min));
        fr.setMetragemMax(max == null ? null : new BigDecimal(max));
        fr.setRotulo("faixa " + id);
        return fr;
    }

    private List<FaixaReferencia> bands() {
        return List.of(
                band(1, "0.00", "40.00"),
                band(2, "41.00", "70.00"),
                band(3, "71.00", "120.00"),
                band(4, "121.00", "195.00"),
                band(5, "195.01", null));
    }

    private ContratoFaixa faixaRow(UUID id, int faixaRefId, String semMob, String comMob) {
        ContratoFaixa f = new ContratoFaixa();
        f.setId(id);
        f.setContratoId(CONTRATO_ID);
        f.setServicoId(SERVICO_ID);
        f.setFaixaReferenciaId((short) faixaRefId);
        f.setValorSemMob(new BigDecimal(semMob));
        f.setValorComMob(new BigDecimal(comMob));
        return f;
    }


    @Test
    @DisplayName("contratoAtivo returns active contract")
    void contratoAtivo_found() {
        Contrato c = new Contrato();
        c.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        c.setStatus("ativo");

        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo"))
                .thenReturn(Optional.of(c));

        Optional<Contrato> result = service.contratoAtivo(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    @DisplayName("contratoAtivo returns empty when no active contract")
    void contratoAtivo_notFound() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo"))
                .thenReturn(Optional.empty());

        Optional<Contrato> result = service.contratoAtivo(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        assertThat(result).isEmpty();
    }


    @Test
    @DisplayName("resolver with percentual mode calculates price from base")
    void resolver_percentual_mode() {
        Contrato c = new Contrato();
        c.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        c.setModo(Contrato.Modo.percentual);
        c.setPercentual(new BigDecimal("10.00"));

        ContratoServico svc = new ContratoServico();
        svc.setAtivo(true);

        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo"))
                .thenReturn(Optional.of(c));
        when(servicos.findFirstByContratoIdAndTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000200")))
                .thenReturn(Optional.of(svc));

        BigDecimal base = new BigDecimal("1000.00");
        PrecoService.Preco resultado = service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), UUID.fromString("00000000-0000-0000-0000-000000000200"), new BigDecimal("100"),
                false, base);

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(resultado.repasse()).isNull();
        assertThat(resultado.faixaId()).isNull();
    }

    @Test
    @DisplayName("resolver percentual applies the minimum floor when %*base is lower")
    void resolver_percentual_aplicaMinimo() {
        Contrato c = new Contrato();
        c.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        c.setModo(Contrato.Modo.percentual);
        c.setPercentual(new BigDecimal("5.00"));
        c.setPercentualMinimo(new BigDecimal("120.00"));

        ContratoServico svc = new ContratoServico();
        svc.setAtivo(true);

        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(c));
        when(servicos.findFirstByContratoIdAndTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000200"))).thenReturn(Optional.of(svc));

        PrecoService.Preco resultado = service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), UUID.fromString("00000000-0000-0000-0000-000000000200"), new BigDecimal("100"),
                false, new BigDecimal("1000.00"));
        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    @DisplayName("resolver percentual throws when baseParaPercentual is null")
    void resolver_percentual_noBase_throws() {
        Contrato c = new Contrato();
        c.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        c.setModo(Contrato.Modo.percentual);

        ContratoServico svc = new ContratoServico();
        svc.setAtivo(true);

        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo"))
                .thenReturn(Optional.of(c));
        when(servicos.findFirstByContratoIdAndTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000200")))
                .thenReturn(Optional.of(svc));

        assertThatThrownBy(() -> service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), UUID.fromString("00000000-0000-0000-0000-000000000200"), new BigDecimal("100"),
                false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base");
    }

    @Test
    @DisplayName("resolver percentual: valor por Serviço guarda-chuva tem precedência sobre o contrato")
    void resolver_percentual_grupoOverride() {
        Contrato c = new Contrato();
        c.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        c.setModo(Contrato.Modo.percentual);
        c.setPercentual(new BigDecimal("10.00"));

        ContratoServico svc = new ContratoServico();
        svc.setAtivo(true);

        ContratoServicoGrupo grupo = new ContratoServicoGrupo();
        grupo.setPercentual(new BigDecimal("20.00"));

        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(c));
        when(servicos.findFirstByContratoIdAndTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000200"))).thenReturn(Optional.of(svc));
        when(servicoTipos.findByTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000200"))).thenReturn(List.of(servicoTipo()));
        when(grupos.findFirstByContratoIdAndServicoId(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000900"))).thenReturn(Optional.of(grupo));

        PrecoService.Preco resultado = service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), UUID.fromString("00000000-0000-0000-0000-000000000200"), new BigDecimal("100"),
                false, new BigDecimal("1000.00"));

        // 1000 * 20% = 200 (percentual do grupo, não os 10% do contrato)
        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("resolver throws when servico not ativo")
    void resolver_servicoNotAtivo_throws() {
        Contrato c = new Contrato();
        c.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        ContratoServico svc = new ContratoServico();
        svc.setAtivo(false);

        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo"))
                .thenReturn(Optional.of(c));
        when(servicos.findFirstByContratoIdAndTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000200")))
                .thenReturn(Optional.of(svc));

        assertThatThrownBy(() -> service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), UUID.fromString("00000000-0000-0000-0000-000000000200"), new BigDecimal("100"),
                false, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Servico");
    }

    @Test
    @DisplayName("resolver throws when servico not found")
    void resolver_servicoNotFound_throws() {
        Contrato c = new Contrato();
        c.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo"))
                .thenReturn(Optional.of(c));
        when(servicos.findFirstByContratoIdAndTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000200")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), UUID.fromString("00000000-0000-0000-0000-000000000200"), new BigDecimal("100"),
                false, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("resolver throws when no active contract")
    void resolver_noContrato_throws() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), UUID.fromString("00000000-0000-0000-0000-000000000200"), new BigDecimal("100"),
                false, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Contrato");
    }

    @Test
    @DisplayName("resolver with percentual rounding")
    void resolver_percentual_rounding() {
        Contrato c = new Contrato();
        c.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        c.setModo(Contrato.Modo.percentual);
        c.setPercentual(new BigDecimal("15.50"));

        ContratoServico svc = new ContratoServico();
        svc.setAtivo(true);

        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo"))
                .thenReturn(Optional.of(c));
        when(servicos.findFirstByContratoIdAndTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000200")))
                .thenReturn(Optional.of(svc));

        BigDecimal base = new BigDecimal("999.99");
        PrecoService.Preco resultado = service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), UUID.fromString("00000000-0000-0000-0000-000000000200"), new BigDecimal("100"),
                false, base);

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("155.00"));
    }


    private void stubServicoGuardaChuvaComFaixas() {
        when(servicoTipos.findByTipoServicoId(TIPO_SERVICO_ID)).thenReturn(List.of(servicoTipo()));
        when(grupos.findFirstByContratoIdAndServicoId(CONTRATO_ID, SERVICO_ID))
                .thenReturn(Optional.of(new ContratoServicoGrupo()));
        when(faixas.findByContratoIdAndServicoId(CONTRATO_ID, SERVICO_ID))
                .thenReturn(List.of(faixaRow(UUID.fromString("00000000-0000-0000-0000-000000000001"), 1, "0", "0")));
    }

    @Test
    @DisplayName("resolver faixa: banda normal usa a linha (contrato × serviço × faixa_referencia)")
    void resolver_faixa_bandaNormal() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(contratoFaixa()));
        stubServicoAtivo();
        stubServicoGuardaChuvaComFaixas();
        when(faixaReferencia.findAllByOrderByOrdemAsc()).thenReturn(bands());

        // metragem 100 -> faixa de referência 3 (71–120)
        ContratoFaixa row = faixaRow(UUID.fromString("00000000-0000-0000-0000-000000000010"), 3, "300.00", "400.00");
        when(faixas.findFirstByContratoIdAndServicoIdAndFaixaReferenciaId(CONTRATO_ID, SERVICO_ID, (short) 3))
                .thenReturn(Optional.of(row));

        ContratoRepasse repasse = new ContratoRepasse();
        repasse.setValorRepasseSemMob(new BigDecimal("150.00"));
        repasse.setValorRepasseComMob(new BigDecimal("200.00"));
        when(repasses.findFirstByContratoFaixaId(UUID.fromString("00000000-0000-0000-0000-000000000010"))).thenReturn(Optional.of(repasse));

        PrecoService.Preco resultado = service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), TIPO_SERVICO_ID, new BigDecimal("100"),
                false, null);

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(resultado.repasse()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(resultado.faixaId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000010")); // id da linha contrato_faixas (linkagem de repasse)
    }

    @Test
    @DisplayName("resolver faixa: variante mobiliado usa valor_com_mob e repasse_com_mob")
    void resolver_faixa_mobiliado() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(contratoFaixa()));
        stubServicoAtivo();
        stubServicoGuardaChuvaComFaixas();
        when(faixaReferencia.findAllByOrderByOrdemAsc()).thenReturn(bands());

        ContratoFaixa row = faixaRow(UUID.fromString("00000000-0000-0000-0000-000000000010"), 3, "300.00", "450.00");
        when(faixas.findFirstByContratoIdAndServicoIdAndFaixaReferenciaId(CONTRATO_ID, SERVICO_ID, (short) 3))
                .thenReturn(Optional.of(row));

        ContratoRepasse repasse = new ContratoRepasse();
        repasse.setValorRepasseSemMob(new BigDecimal("150.00"));
        repasse.setValorRepasseComMob(new BigDecimal("225.00"));
        when(repasses.findFirstByContratoFaixaId(UUID.fromString("00000000-0000-0000-0000-000000000010"))).thenReturn(Optional.of(repasse));

        PrecoService.Preco resultado = service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), TIPO_SERVICO_ID, new BigDecimal("100"),
                true, null);

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("450.00"));
        assertThat(resultado.repasse()).isEqualByComparingTo(new BigDecimal("225.00"));
    }

    @Test
    @DisplayName("resolver faixa: banda 5 soma o incremento a cada passo de +75 m² completo")
    void resolver_faixa_banda5_passoCompleto() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(contratoFaixa()));
        stubServicoAtivo();
        stubServicoGuardaChuvaComFaixas();
        when(faixaReferencia.findAllByOrderByOrdemAsc()).thenReturn(bands());

        ContratoFaixa row = faixaRow(UUID.fromString("00000000-0000-0000-0000-000000000010"), 5, "300.00", "400.00");
        row.setPaStepMetragem(new BigDecimal("75"));
        row.setPaStepValor(new BigDecimal("50.00"));
        when(faixas.findFirstByContratoIdAndServicoIdAndFaixaReferenciaId(CONTRATO_ID, SERVICO_ID, (short) 5))
                .thenReturn(Optional.of(row));
        when(repasses.findFirstByContratoFaixaId(UUID.fromString("00000000-0000-0000-0000-000000000010"))).thenReturn(Optional.empty());

        // metragem 350: excedente 350 - 195.01 = 154.99; floor(154.99 / 75) = 2 passos
        // 2 * 50.00 = 100.00; 300 + 100 = 400
        PrecoService.Preco resultado = service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), TIPO_SERVICO_ID, new BigDecimal("350"),
                false, null);

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    @DisplayName("resolver faixa: banda 5 usa FLOOR — excedente parcial não antecipa o próximo degrau")
    void resolver_faixa_banda5_floorParcial() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(contratoFaixa()));
        stubServicoAtivo();
        stubServicoGuardaChuvaComFaixas();
        when(faixaReferencia.findAllByOrderByOrdemAsc()).thenReturn(bands());

        ContratoFaixa row = faixaRow(UUID.fromString("00000000-0000-0000-0000-000000000010"), 5, "300.00", "400.00");
        row.setPaStepMetragem(new BigDecimal("75"));
        row.setPaStepValor(new BigDecimal("50.00"));
        when(faixas.findFirstByContratoIdAndServicoIdAndFaixaReferenciaId(CONTRATO_ID, SERVICO_ID, (short) 5))
                .thenReturn(Optional.of(row));
        when(repasses.findFirstByContratoFaixaId(UUID.fromString("00000000-0000-0000-0000-000000000010"))).thenReturn(Optional.empty());

        // metragem 260: excedente 260 - 195.01 = 64.99; floor(64.99 / 75) = 0 passos completos.
        // 0 * 50.00 = 0; valor = base 300.00 (o excedente parcial NÃO cobra o próximo degrau).
        PrecoService.Preco resultado = service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), TIPO_SERVICO_ID, new BigDecimal("260"),
                false, null);

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("resolver faixa: banda 5 usa paStepMetragem default 75 quando nulo")
    void resolver_faixa_banda5_stepDefault75() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(contratoFaixa()));
        stubServicoAtivo();
        stubServicoGuardaChuvaComFaixas();
        when(faixaReferencia.findAllByOrderByOrdemAsc()).thenReturn(bands());

        ContratoFaixa row = faixaRow(UUID.fromString("00000000-0000-0000-0000-000000000010"), 5, "300.00", "400.00");
        row.setPaStepMetragem(null); // default 75
        row.setPaStepValor(new BigDecimal("40.00"));
        when(faixas.findFirstByContratoIdAndServicoIdAndFaixaReferenciaId(CONTRATO_ID, SERVICO_ID, (short) 5))
                .thenReturn(Optional.of(row));
        when(repasses.findFirstByContratoFaixaId(UUID.fromString("00000000-0000-0000-0000-000000000010"))).thenReturn(Optional.empty());

        // metragem 345: excedente 149.99; floor(149.99 / 75) = 1 passo; 300 + 40 = 340
        PrecoService.Preco resultado = service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), TIPO_SERVICO_ID, new BigDecimal("345"),
                false, null);

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("340.00"));
    }

    @Test
    @DisplayName("resolver faixa: banda 5 sem paStepValor -> cobra só o valor base da faixa")
    void resolver_faixa_banda5_semStepValor() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(contratoFaixa()));
        stubServicoAtivo();
        stubServicoGuardaChuvaComFaixas();
        when(faixaReferencia.findAllByOrderByOrdemAsc()).thenReturn(bands());

        ContratoFaixa row = faixaRow(UUID.fromString("00000000-0000-0000-0000-000000000010"), 5, "500.00", "700.00");
        // sem paStepMetragem nem paStepValor
        when(faixas.findFirstByContratoIdAndServicoIdAndFaixaReferenciaId(CONTRATO_ID, SERVICO_ID, (short) 5))
                .thenReturn(Optional.of(row));
        when(repasses.findFirstByContratoFaixaId(UUID.fromString("00000000-0000-0000-0000-000000000010"))).thenReturn(Optional.empty());

        PrecoService.Preco resultado = service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), TIPO_SERVICO_ID, new BigDecimal("400"),
                false, null);

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("resolver faixa: sem grupo (Serviço não oferecido no contrato) -> NotFoundException")
    void resolver_faixa_semGrupo_throws() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(contratoFaixa()));
        stubServicoAtivo();
        when(servicoTipos.findByTipoServicoId(TIPO_SERVICO_ID)).thenReturn(List.of(servicoTipo()));
        when(grupos.findFirstByContratoIdAndServicoId(CONTRATO_ID, SERVICO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), TIPO_SERVICO_ID, new BigDecimal("100"),
                false, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("sem faixas configuradas");
    }

    @Test
    @DisplayName("resolver faixa: grupo existe mas sem linhas contrato_faixas -> NotFoundException")
    void resolver_faixa_grupoSemFaixas_throws() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(contratoFaixa()));
        stubServicoAtivo();
        when(servicoTipos.findByTipoServicoId(TIPO_SERVICO_ID)).thenReturn(List.of(servicoTipo()));
        when(grupos.findFirstByContratoIdAndServicoId(CONTRATO_ID, SERVICO_ID))
                .thenReturn(Optional.of(new ContratoServicoGrupo()));
        when(faixas.findByContratoIdAndServicoId(CONTRATO_ID, SERVICO_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), TIPO_SERVICO_ID, new BigDecimal("100"),
                false, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("sem faixas configuradas");
    }

    @Test
    @DisplayName("resolver faixa: linha ausente para a faixa de referência resolvida -> NotFoundException")
    void resolver_faixa_linhaFaixaAusente_throws() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(contratoFaixa()));
        stubServicoAtivo();
        stubServicoGuardaChuvaComFaixas();
        when(faixaReferencia.findAllByOrderByOrdemAsc()).thenReturn(bands());
        when(faixas.findFirstByContratoIdAndServicoIdAndFaixaReferenciaId(CONTRATO_ID, SERVICO_ID, (short) 3))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), TIPO_SERVICO_ID, new BigDecimal("100"),
                false, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("faixa de referencia");
    }

    @Test
    @DisplayName("resolver faixa: metragem fora de todas as faixas de referência -> NotFoundException")
    void resolver_faixa_metragemForaDasFaixas_throws() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(contratoFaixa()));
        stubServicoAtivo();
        stubServicoGuardaChuvaComFaixas();
        when(faixaReferencia.findAllByOrderByOrdemAsc()).thenReturn(bands());

        assertThatThrownBy(() -> service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), TIPO_SERVICO_ID, new BigDecimal("-1"),
                false, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("fora das faixas de referencia");
    }

    @Test
    @DisplayName("resolver faixa: repasse nulo quando não há linha de repasse")
    void resolver_faixa_semRepasse() {
        when(contratos.findFirstByImobiliariaIdAndStatus(UUID.fromString("00000000-0000-0000-0000-000000000100"), "ativo")).thenReturn(Optional.of(contratoFaixa()));
        stubServicoAtivo();
        stubServicoGuardaChuvaComFaixas();
        when(faixaReferencia.findAllByOrderByOrdemAsc()).thenReturn(bands());

        ContratoFaixa row = faixaRow(UUID.fromString("00000000-0000-0000-0000-000000000010"), 2, "300.00", "400.00");
        when(faixas.findFirstByContratoIdAndServicoIdAndFaixaReferenciaId(CONTRATO_ID, SERVICO_ID, (short) 2))
                .thenReturn(Optional.of(row));
        when(repasses.findFirstByContratoFaixaId(UUID.fromString("00000000-0000-0000-0000-000000000010"))).thenReturn(Optional.empty());

        PrecoService.Preco resultado = service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000100"), TIPO_SERVICO_ID, new BigDecimal("50"),
                false, null);

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(resultado.repasse()).isNull();
    }


    @Test
    @DisplayName("resolverB2c: modo percentual aplica o percentual sobre o valor do aluguel")
    void resolverB2c_percentual() {
        var config = new br.com.vistoria.core.modules.biletagemcliente.BiletagemCliente();
        config.setModo(br.com.vistoria.core.modules.biletagemcliente.BiletagemCliente.Modo.percentual);
        when(biletagemCliente.findById(Boolean.TRUE)).thenReturn(Optional.of(config));

        var p = new br.com.vistoria.core.modules.biletagemcliente.BiletagemClientePercentual();
        p.setB2cServiceId(SERVICO_ENTRADA);
        p.setPercentual(new BigDecimal("10"));
        p.setMinimo(new BigDecimal("50.00"));
        when(biletagemClientePercentual.findById(SERVICO_ENTRADA))
                .thenReturn(Optional.of(p));

        PrecoService.Preco resultado = service.resolverB2c(
                SERVICO_ENTRADA,
                null, new BigDecimal("2000.00"), null);

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(resultado.repasse()).isNull();
    }

    @Test
    @DisplayName("resolverB2c: modo percentual aplica o minimo quando o percentual fica abaixo")
    void resolverB2c_percentualAplicaMinimo() {
        var config = new br.com.vistoria.core.modules.biletagemcliente.BiletagemCliente();
        config.setModo(br.com.vistoria.core.modules.biletagemcliente.BiletagemCliente.Modo.percentual);
        when(biletagemCliente.findById(Boolean.TRUE)).thenReturn(Optional.of(config));

        var p = new br.com.vistoria.core.modules.biletagemcliente.BiletagemClientePercentual();
        p.setB2cServiceId(SERVICO_SAIDA);
        p.setPercentual(new BigDecimal("5"));
        p.setMinimo(new BigDecimal("150.00"));
        when(biletagemClientePercentual.findById(SERVICO_SAIDA))
                .thenReturn(Optional.of(p));

        PrecoService.Preco resultado = service.resolverB2c(
                SERVICO_SAIDA,
                null, new BigDecimal("1000.00"), null);

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("resolverB2c: modo faixa resolve por metragem e mobiliado")
    void resolverB2c_faixa() {
        var config = new br.com.vistoria.core.modules.biletagemcliente.BiletagemCliente();
        config.setModo(br.com.vistoria.core.modules.biletagemcliente.BiletagemCliente.Modo.faixa);
        when(biletagemCliente.findById(Boolean.TRUE)).thenReturn(Optional.of(config));
        when(faixaReferencia.findAllByOrderByOrdemAsc()).thenReturn(bands());

        var f = new br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteFaixa();
        f.setB2cServiceId(SERVICO_ENTRADA);
        f.setFaixaReferenciaId((short) 2);
        f.setValorSemMob(new BigDecimal("189.00"));
        f.setValorComMob(new BigDecimal("219.00"));
        when(biletagemClienteFaixa.findByB2cServiceIdAndFaixaReferenciaId(
                SERVICO_ENTRADA, (short) 2))
                .thenReturn(Optional.of(f));

        PrecoService.Preco resultado = service.resolverB2c(
                SERVICO_ENTRADA,
                new BigDecimal("50"), null, true);

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("219.00"));
    }

    @Test
    @DisplayName("resolverB2c: sem config -> NotFoundException")
    void resolverB2c_semConfig() {
        when(biletagemCliente.findById(Boolean.TRUE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolverB2c(
                SERVICO_ENTRADA,
                new BigDecimal("50"), null, false))
                .isInstanceOf(NotFoundException.class);
    }
}
