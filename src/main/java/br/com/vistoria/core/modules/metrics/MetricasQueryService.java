package br.com.vistoria.core.modules.metrics;

import br.com.vistoria.core.modules.metrics.MetricsRepositories.EnvioDiarioRepository;
import br.com.vistoria.core.modules.metrics.MetricsRepositories.SolicitacaoDiarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Leitura das metricas para o inspecta-api. Toca somente as tabelas de rollup
 * ({@code metrics.*_diario}) — os dados sao sempre D-1. Nunca chama o inspecta-worker.
 */
@Service
@RequiredArgsConstructor
public class MetricasQueryService {

    /** Janela maxima aceita numa consulta (defende contra varredura acidental). */
    private static final int JANELA_MAX_DIAS = 366;

    private final EnvioDiarioRepository enviosDiario;
    private final SolicitacaoDiarioRepository solicitacoesDiario;

    public record EnvioBucket(LocalDate dia, String tipoEnvio, String tipoComunicacao,
                              long criados, long enviados, long entregues, long aberturas,
                              long cliques, long bounces, long reclamacoes, long falhas) {}

    public record EnviosResposta(LocalDate inicio, LocalDate fim,
                                 List<EnvioBucket> buckets, EnvioBucket totais) {}

    public record SolicitacaoBucket(LocalDate dia, UUID tipoServicoId, String tipoServicoNome,
                                    long concluidas, long emAndamento) {}

    public record SolicitacoesResposta(LocalDate inicio, LocalDate fim, List<SolicitacaoBucket> buckets) {}

    @Transactional(readOnly = true)
    public EnviosResposta envios(LocalDate inicio, LocalDate fim, String tipoEnvio, String tipoComunicacao) {
        validarJanela(inicio, fim);
        String te = blankToNull(tipoEnvio);
        String tc = blankToNull(tipoComunicacao);

        List<EnvioBucket> buckets = enviosDiario.buscar(inicio, fim, te, tc).stream()
                .map(d -> new EnvioBucket(d.getDia(), d.getTipoEnvio(), d.getTipoComunicacao(),
                        d.getCriados(), d.getEnviados(), d.getEntregues(), d.getAberturas(),
                        d.getCliques(), d.getBounces(), d.getReclamacoes(), d.getFalhas()))
                .toList();

        EnvioBucket totais = buckets.stream().reduce(
                new EnvioBucket(null, null, null, 0, 0, 0, 0, 0, 0, 0, 0),
                (a, b) -> new EnvioBucket(null, null, null,
                        a.criados() + b.criados(), a.enviados() + b.enviados(),
                        a.entregues() + b.entregues(), a.aberturas() + b.aberturas(),
                        a.cliques() + b.cliques(), a.bounces() + b.bounces(),
                        a.reclamacoes() + b.reclamacoes(), a.falhas() + b.falhas()));

        return new EnviosResposta(inicio, fim, buckets, totais);
    }

    @Transactional(readOnly = true)
    public SolicitacoesResposta solicitacoes(LocalDate inicio, LocalDate fim) {
        validarJanela(inicio, fim);
        List<SolicitacaoBucket> buckets = solicitacoesDiario.buscar(inicio, fim).stream()
                .map(d -> new SolicitacaoBucket(d.getDia(), d.getTipoServicoId(), d.getTipoServicoNome(),
                        d.getConcluidas(), d.getEmAndamento()))
                .toList();
        return new SolicitacoesResposta(inicio, fim, buckets);
    }

    private void validarJanela(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) throw new IllegalArgumentException("inicio e fim sao obrigatorios");
        if (inicio.isAfter(fim)) throw new IllegalArgumentException("inicio nao pode ser depois de fim");
        if (inicio.plusDays(JANELA_MAX_DIAS).isBefore(fim)) {
            throw new IllegalArgumentException("janela maxima e de " + JANELA_MAX_DIAS + " dias");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
