package br.com.vistoria.core.modules.disponibilidade;

import br.com.vistoria.core.modules.disponibilidade.DisponibilidadeRepositories.ExcecaoRepository;
import br.com.vistoria.core.modules.disponibilidade.DisponibilidadeRepositories.VistoriadorRepository;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoRepositories.ServicoRepository;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoServico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DisponibilidadeService")
class DisponibilidadeServiceTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final UUID CIDADE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock private VistoriadorRepository vistoriadores;
    @Mock private ExcecaoRepository excecoes;
    @Mock private ServicoRepository servicos;

    private DisponibilidadeService service;

    @BeforeEach
    void setUp() {
        service = new DisponibilidadeService(vistoriadores, excecoes, servicos);
    }

    private Vistoriador vistoriador(UUID id, UUID[] cidades, Double latBase, Double lngBase, Integer distMaxKm) {
        Vistoriador v = new Vistoriador();
        v.setId(id);
        v.setUsuarioId(id);
        v.setAtivo(true);
        v.setCidades(cidades);
        v.setLatBase(latBase);
        v.setLngBase(lngBase);
        v.setDistanciaMaxKm(distMaxKm == null ? null : BigDecimal.valueOf(distMaxKm));
        return v;
    }

    private DisponibilidadeExcecao excecao(UUID vistoriadorId) {
        DisponibilidadeExcecao e = new DisponibilidadeExcecao();
        e.setVistoriadorId(vistoriadorId);
        e.setTipo(DisponibilidadeExcecao.Tipo.ferias);
        e.setDataInicio(LocalDate.now(SP).minusDays(1));
        e.setDataFim(LocalDate.now(SP).plusDays(30));
        return e;
    }

    @Test
    @DisplayName("true: active inspector in the city, within range, no dataDesejada -> skips time checks")
    void elegivel_semDataDesejada_true() {
        when(vistoriadores.findByAtivoTrue())
            .thenReturn(List.of(vistoriador(UUID.fromString("00000000-0000-0000-0000-000000000001"), new UUID[]{CIDADE}, -23.55, -46.63, 50)));

        boolean r = service.existeVistoriadorElegivel(CIDADE, -23.56, -46.64, 60, false, null);

        assertThat(r).isTrue();
        verifyNoInteractions(excecoes, servicos);
    }

    @Test
    @DisplayName("true: with dataDesejada, day not blocked and agenda free")
    void elegivel_comDataDesejada_true() {
        when(vistoriadores.findByAtivoTrue())
            .thenReturn(List.of(vistoriador(UUID.fromString("00000000-0000-0000-0000-000000000001"), new UUID[]{CIDADE}, -23.55, -46.63, 50)));
        when(excecoes.findByVistoriadorIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
                eq(UUID.fromString("00000000-0000-0000-0000-000000000001")), any(), any())).thenReturn(List.of());
        when(servicos.agendaOcupada(eq(UUID.fromString("00000000-0000-0000-0000-000000000001")), any(), any())).thenReturn(List.of());

        Instant quando = LocalDate.now(SP).plusDays(2).atTime(10, 0).atZone(SP).toInstant();
        boolean r = service.existeVistoriadorElegivel(CIDADE, -23.56, -46.64, 90, true, quando);

        assertThat(r).isTrue();
    }

    @Test
    @DisplayName("false: nobody covers the property city")
    void elegivel_cidadeForaDaLista_false() {
        when(vistoriadores.findByAtivoTrue())
            .thenReturn(List.of(vistoriador(UUID.fromString("00000000-0000-0000-0000-000000000001"), new UUID[]{UUID.fromString("00000000-0000-0000-0000-000000000099")}, -23.55, -46.63, 50)));

        boolean r = service.existeVistoriadorElegivel(CIDADE, -23.56, -46.64, 60, false, null);

        assertThat(r).isFalse();
    }

    @Test
    @DisplayName("false: property outside the inspector distance limit")
    void elegivel_foraDoRaio_false() {
        // Base in Sao Paulo, 50 km radius; property in Rio (~357 km).
        when(vistoriadores.findByAtivoTrue())
            .thenReturn(List.of(vistoriador(UUID.fromString("00000000-0000-0000-0000-000000000001"), new UUID[]{CIDADE}, -23.55, -46.63, 50)));

        boolean r = service.existeVistoriadorElegivel(CIDADE, -22.90, -43.21, 60, false, null);

        assertThat(r).isFalse();
    }

    @Test
    @DisplayName("false: the desired day is fully blocked by an exception")
    void elegivel_diaBloqueado_false() {
        when(vistoriadores.findByAtivoTrue())
            .thenReturn(List.of(vistoriador(UUID.fromString("00000000-0000-0000-0000-000000000001"), new UUID[]{CIDADE}, -23.55, -46.63, 50)));
        when(excecoes.findByVistoriadorIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
                eq(UUID.fromString("00000000-0000-0000-0000-000000000001")), any(), any())).thenReturn(List.of(excecao(UUID.fromString("00000000-0000-0000-0000-000000000001"))));

        Instant quando = LocalDate.now(SP).plusDays(2).atTime(10, 0).atZone(SP).toInstant();
        boolean r = service.existeVistoriadorElegivel(CIDADE, -23.56, -46.64, 60, false, quando);

        assertThat(r).isFalse();
        verify(servicos, never()).agendaOcupada(any(), any(), any());
    }

    @Test
    @DisplayName("false: the agenda already has a conflicting service at that time")
    void elegivel_agendaOcupada_false() {
        when(vistoriadores.findByAtivoTrue())
            .thenReturn(List.of(vistoriador(UUID.fromString("00000000-0000-0000-0000-000000000001"), new UUID[]{CIDADE}, -23.55, -46.63, 50)));
        when(excecoes.findByVistoriadorIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
                eq(UUID.fromString("00000000-0000-0000-0000-000000000001")), any(), any())).thenReturn(List.of());
        SolicitacaoServico conflito = new SolicitacaoServico();
        conflito.setId(UUID.fromString("00000000-0000-0000-0000-000000000009"));
        when(servicos.agendaOcupada(eq(UUID.fromString("00000000-0000-0000-0000-000000000001")), any(), any())).thenReturn(List.of(conflito));

        Instant quando = LocalDate.now(SP).plusDays(2).atTime(10, 0).atZone(SP).toInstant();
        boolean r = service.existeVistoriadorElegivel(CIDADE, -23.56, -46.64, 60, false, quando);

        assertThat(r).isFalse();
    }

    @Test
    @DisplayName("false: there are no active inspectors")
    void elegivel_semVistoriadores_false() {
        when(vistoriadores.findByAtivoTrue()).thenReturn(List.of());

        boolean r = service.existeVistoriadorElegivel(CIDADE, -23.56, -46.64, 60, false, null);

        assertThat(r).isFalse();
        verifyNoInteractions(excecoes, servicos);
    }

    @Test
    @DisplayName("distance passes when the inspector has no configured limit / no base coordinates")
    void atendeLocal_semLimiteDeDistancia_true() {
        Vistoriador semLimite = vistoriador(UUID.fromString("00000000-0000-0000-0000-000000000001"), new UUID[]{CIDADE}, null, null, null);

        assertThat(service.atendeLocal(semLimite, CIDADE, -22.90, -43.21)).isTrue();
    }

    @Test
    @DisplayName("atendeLocal is false for an inactive inspector")
    void atendeLocal_inativo_false() {
        Vistoriador inativo = vistoriador(UUID.fromString("00000000-0000-0000-0000-000000000001"), new UUID[]{CIDADE}, -23.55, -46.63, 50);
        inativo.setAtivo(false);

        assertThat(service.atendeLocal(inativo, CIDADE, -23.56, -46.64)).isFalse();
    }

    @Test
    @DisplayName("diaBloqueado reflects the exception repository result")
    void diaBloqueado_delegates() {
        LocalDate dia = LocalDate.now(SP);
        when(excecoes.findByVistoriadorIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(UUID.fromString("00000000-0000-0000-0000-000000000001"), dia, dia))
            .thenReturn(List.of(excecao(UUID.fromString("00000000-0000-0000-0000-000000000001"))));

        assertThat(service.diaBloqueado(UUID.fromString("00000000-0000-0000-0000-000000000001"), dia)).isTrue();
    }

    @Test
    @DisplayName("haversineKm: Sao Paulo -> Rio is ~357 km great-circle and symmetric")
    void haversineKm_valores() {
        double d = DisponibilidadeService.haversineKm(-23.55, -46.63, -22.90, -43.21);
        assertThat(d).isCloseTo(357.0, org.assertj.core.api.Assertions.within(3.0));
        assertThat(DisponibilidadeService.haversineKm(-22.90, -43.21, -23.55, -46.63))
            .isCloseTo(d, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(DisponibilidadeService.haversineKm(1.0, 1.0, 1.0, 1.0)).isCloseTo(0.0, org.assertj.core.api.Assertions.within(1e-9));
    }


    @Test
    @DisplayName("escolher: devolve o vistoriador elegivel (nao so um booleano)")
    void escolher_devolveVistoriador() {
        UUID vistoriadorId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Vistoriador v = vistoriador(vistoriadorId, new UUID[]{CIDADE}, -23.55, -46.63, 50);
        when(vistoriadores.findByAtivoTrue()).thenReturn(List.of(v));

        var resultado = service.escolher(CIDADE, -23.56, -46.64, 60, null);

        assertThat(resultado).contains(v);
    }

    @Test
    @DisplayName("escolher: primeiro elegivel na ordem de findByAtivoTrue quando ha mais de um")
    void escolher_primeiroElegivel() {
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Vistoriador primeiro = vistoriador(id1, new UUID[]{CIDADE}, -23.55, -46.63, 50);
        Vistoriador segundo = vistoriador(id2, new UUID[]{CIDADE}, -23.55, -46.63, 50);
        when(vistoriadores.findByAtivoTrue()).thenReturn(List.of(primeiro, segundo));

        var resultado = service.escolher(CIDADE, -23.56, -46.64, 60, null);

        assertThat(resultado).contains(primeiro);
    }

    @Test
    @DisplayName("escolher: pula o inelegivel e devolve o proximo elegivel")
    void escolher_pulaInelegivel() {
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Vistoriador foraDaCidade = vistoriador(id1, new UUID[]{UUID.fromString("00000000-0000-0000-0000-000000000099")}, null, null, null);
        Vistoriador elegivel = vistoriador(id2, new UUID[]{CIDADE}, -23.55, -46.63, 50);
        when(vistoriadores.findByAtivoTrue()).thenReturn(List.of(foraDaCidade, elegivel));

        var resultado = service.escolher(CIDADE, -23.56, -46.64, 60, null);

        assertThat(resultado).contains(elegivel);
    }

    @Test
    @DisplayName("escolher: Optional vazio quando ninguem e elegivel")
    void escolher_vazio() {
        when(vistoriadores.findByAtivoTrue()).thenReturn(List.of());

        assertThat(service.escolher(CIDADE, -23.56, -46.64, 60, null)).isEmpty();
    }
}
