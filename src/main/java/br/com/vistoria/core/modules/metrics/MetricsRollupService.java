package br.com.vistoria.core.modules.metrics;

import br.com.vistoria.core.modules.metrics.MetricsRepositories.EnvioDiarioRepository;
import br.com.vistoria.core.modules.metrics.MetricsRepositories.SolicitacaoDiarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Recalcula os rollups diarios D-1 (envios + solicitacoes). Chamado pelo cron do
 * inspecta-worker para D-1..D-N (captura webhooks atrasados). Idempotente por upsert.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsRollupService {

    private final EnvioDiarioRepository enviosDiario;
    private final SolicitacaoDiarioRepository solicitacoesDiario;

    @Transactional
    public void rolarDia(LocalDate dia) {
        int envios = enviosDiario.rolarEnviosDoDia(dia);
        int solicitacoes = solicitacoesDiario.rolarSolicitacoesDoDia(dia);
        log.info("[metrics] rollup dia={} envios={} solicitacoes={}", dia, envios, solicitacoes);
    }
}
