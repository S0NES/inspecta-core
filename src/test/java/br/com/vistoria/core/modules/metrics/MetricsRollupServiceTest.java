package br.com.vistoria.core.modules.metrics;

import br.com.vistoria.core.modules.metrics.MetricsRepositories.EnvioDiarioRepository;
import br.com.vistoria.core.modules.metrics.MetricsRepositories.SolicitacaoDiarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsRollupService")
class MetricsRollupServiceTest {

    @Mock private EnvioDiarioRepository enviosDiario;
    @Mock private SolicitacaoDiarioRepository solicitacoesDiario;
    private MetricsRollupService service;

    @BeforeEach
    void setUp() {
        service = new MetricsRollupService(enviosDiario, solicitacoesDiario);
    }

    @Test
    @DisplayName("rolarDia recomputes both rollups for the given day")
    void rolarDia() {
        LocalDate dia = LocalDate.of(2026, 8, 26);
        when(enviosDiario.rolarEnviosDoDia(dia)).thenReturn(3);
        when(solicitacoesDiario.rolarSolicitacoesDoDia(dia)).thenReturn(5);

        service.rolarDia(dia);

        verify(enviosDiario).rolarEnviosDoDia(dia);
        verify(solicitacoesDiario).rolarSolicitacoesDoDia(dia);
    }
}
