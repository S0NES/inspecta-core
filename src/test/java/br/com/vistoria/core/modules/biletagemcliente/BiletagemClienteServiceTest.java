package br.com.vistoria.core.modules.biletagemcliente;

import br.com.vistoria.core.modules.b2cservico.B2cServiceRepository;
import br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteRepositories.BiletagemClienteFaixaRepository;
import br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteRepositories.BiletagemClientePercentualRepository;
import br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteRepositories.BiletagemClienteRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BiletagemClienteService")
class BiletagemClienteServiceTest {

    private static final UUID SERVICO_ENTRADA = UUID.fromString("00000000-b2c2-0000-0000-000000000001");
    private static final UUID SERVICO_SAIDA = UUID.fromString("00000000-b2c2-0000-0000-000000000002");

    @Mock private BiletagemClienteRepository config;
    @Mock private BiletagemClientePercentualRepository percentuais;
    @Mock private BiletagemClienteFaixaRepository faixas;
    @Mock private B2cServiceRepository b2cServicos;

    private BiletagemClienteService service;

    @BeforeEach
    void setUp() {
        service = new BiletagemClienteService(config, percentuais, faixas, b2cServicos);
    }

    @Test
    @DisplayName("get: sem config gravada, default modo=faixa e listas vazias")
    void get_semConfigGravada() {
        when(config.findById(Boolean.TRUE)).thenReturn(Optional.empty());
        when(b2cServicos.findAllByAtivoTrueOrderByOrdemAscNomeAsc()).thenReturn(List.of());
        when(percentuais.findAll()).thenReturn(List.of());
        when(faixas.findAll()).thenReturn(List.of());

        var view = service.get();

        assertThat(view.modo()).isEqualTo(BiletagemCliente.Modo.faixa);
        assertThat(view.servicos()).isEmpty();
        assertThat(view.percentuais()).isEmpty();
        assertThat(view.faixas()).isEmpty();
    }

    @Test
    @DisplayName("get: reflete a config e as linhas gravadas")
    void get_comConfigGravada() {
        BiletagemCliente c = new BiletagemCliente();
        c.setModo(BiletagemCliente.Modo.percentual);
        when(config.findById(Boolean.TRUE)).thenReturn(Optional.of(c));
        when(b2cServicos.findAllByAtivoTrueOrderByOrdemAscNomeAsc()).thenReturn(List.of());

        BiletagemClientePercentual p = new BiletagemClientePercentual();
        p.setB2cServiceId(SERVICO_ENTRADA);
        p.setPercentual(new BigDecimal("10"));
        p.setMinimo(new BigDecimal("50.00"));
        when(percentuais.findAll()).thenReturn(List.of(p));
        when(faixas.findAll()).thenReturn(List.of());

        var view = service.get();

        assertThat(view.modo()).isEqualTo(BiletagemCliente.Modo.percentual);
        assertThat(view.percentuais()).hasSize(1);
        assertThat(view.percentuais().get(0).b2cServiceId()).isEqualTo(SERVICO_ENTRADA);
        assertThat(view.percentuais().get(0).percentual()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("salvar: modo percentual sem nenhuma linha -> IllegalArgumentException, nunca grava o modo")
    void salvar_percentualVazio() {
        assertThatThrownBy(() -> service.salvar(BiletagemCliente.Modo.percentual, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("salvar: modo faixa sem nenhuma linha -> IllegalArgumentException")
    void salvar_faixaVazia() {
        assertThatThrownBy(() -> service.salvar(BiletagemCliente.Modo.faixa, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("salvar: b2c_service_id desconhecido -> IllegalArgumentException")
    void salvar_servicoInvalido() {
        when(b2cServicos.existsById(any())).thenReturn(false);
        var itens = List.of(new BiletagemClienteService.PercentualItem(SERVICO_ENTRADA, new BigDecimal("10"), null));

        assertThatThrownBy(() -> service.salvar(BiletagemCliente.Modo.percentual, itens, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(config, never()).save(any());
    }

    @Test
    @DisplayName("salvar: modo percentual substitui todas as linhas de percentual (delete-then-insert)")
    void salvar_percentualSubstituiLinhas() {
        when(b2cServicos.existsById(any())).thenReturn(true);
        when(config.findById(Boolean.TRUE)).thenReturn(Optional.empty());
        when(config.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(b2cServicos.findAllByAtivoTrueOrderByOrdemAscNomeAsc()).thenReturn(List.of());
        when(percentuais.findAll()).thenReturn(List.of());
        when(faixas.findAll()).thenReturn(List.of());

        var itens = List.of(
                new BiletagemClienteService.PercentualItem(SERVICO_ENTRADA, new BigDecimal("10"), new BigDecimal("50")),
                new BiletagemClienteService.PercentualItem(SERVICO_SAIDA, new BigDecimal("8"), null));

        service.salvar(BiletagemCliente.Modo.percentual, itens, null);

        verify(percentuais).deleteAll();
        verify(percentuais, times(2)).save(any());
        verify(config).save(argThat(c -> c.getModo() == BiletagemCliente.Modo.percentual));
        verify(faixas, never()).deleteAll();
        verify(faixas, never()).save(any());
    }

    @Test
    @DisplayName("salvar: modo faixa substitui todas as linhas de faixa (delete-then-insert), nao mexe em percentual")
    void salvar_faixaSubstituiLinhas() {
        when(b2cServicos.existsById(any())).thenReturn(true);
        when(config.findById(Boolean.TRUE)).thenReturn(Optional.empty());
        when(config.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(b2cServicos.findAllByAtivoTrueOrderByOrdemAscNomeAsc()).thenReturn(List.of());
        when(percentuais.findAll()).thenReturn(List.of());
        when(faixas.findAll()).thenReturn(List.of());

        var itens = List.of(new BiletagemClienteService.FaixaItem(
                SERVICO_ENTRADA, (short) 1, new BigDecimal("189.00"), new BigDecimal("219.00")));

        service.salvar(BiletagemCliente.Modo.faixa, null, itens);

        verify(faixas).deleteAll();
        verify(faixas, times(1)).save(any());
        verify(percentuais, never()).deleteAll();
        verify(percentuais, never()).save(any());
    }
}
