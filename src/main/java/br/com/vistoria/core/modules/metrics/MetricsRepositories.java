package br.com.vistoria.core.modules.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetricsRepositories {

    interface EnvioRepository extends JpaRepository<MetricaEnvio, UUID> {
        Optional<MetricaEnvio> findByMensagemId(String mensagemId);
        Optional<MetricaEnvio> findFirstByExternalIdOrderByIdDesc(String externalId);
    }

    interface EnvioDiarioRepository extends JpaRepository<MetricaEnvioDiario, MetricaEnvioDiario.Pk> {

        /**
         * Recalcula o rollup do dia a partir de {@code metrics.envios} (bucket = data_criacao).
         * Idempotente: {@code ON CONFLICT DO UPDATE}. Nao remove linhas que ficaram sem base
         * (a base so cresce enquanto nao houver retencao).
         */
        @Modifying
        @Query(nativeQuery = true, value = """
            INSERT INTO metrics.envios_diario AS d
              (dia, tipo_envio, tipo_comunicacao, criados, enviados, entregues,
               aberturas, cliques, bounces, reclamacoes, falhas, atualizado_em)
            SELECT :dia, e.tipo_envio, e.tipo_comunicacao,
                   count(*),
                   count(*) FILTER (WHERE e.data_envio IS NOT NULL),
                   count(*) FILTER (WHERE e.status IN ('entregue','aberto','clicado')),
                   count(*) FILTER (WHERE e.status IN ('aberto','clicado')),
                   count(*) FILTER (WHERE e.status = 'clicado'),
                   count(*) FILTER (WHERE e.status = 'bounce'),
                   count(*) FILTER (WHERE e.status = 'reclamacao'),
                   count(*) FILTER (WHERE e.status = 'falha'),
                   now()
            FROM metrics.envios e
            WHERE e.data_criacao >= CAST(:dia AS timestamptz)
              AND e.data_criacao <  CAST(CAST(:dia AS date) + 1 AS timestamptz)
            GROUP BY e.tipo_envio, e.tipo_comunicacao
            ON CONFLICT (dia, tipo_envio, tipo_comunicacao) DO UPDATE SET
              criados = EXCLUDED.criados, enviados = EXCLUDED.enviados, entregues = EXCLUDED.entregues,
              aberturas = EXCLUDED.aberturas, cliques = EXCLUDED.cliques, bounces = EXCLUDED.bounces,
              reclamacoes = EXCLUDED.reclamacoes, falhas = EXCLUDED.falhas, atualizado_em = now()
            """)
        int rolarEnviosDoDia(@Param("dia") LocalDate dia);

        @Query("""
            SELECT d FROM MetricaEnvioDiario d
            WHERE d.dia BETWEEN :inicio AND :fim
              AND (:tipoEnvio IS NULL OR d.tipoEnvio = :tipoEnvio)
              AND (:tipoComunicacao IS NULL OR d.tipoComunicacao = :tipoComunicacao)
            ORDER BY d.dia, d.tipoEnvio, d.tipoComunicacao
            """)
        List<MetricaEnvioDiario> buscar(@Param("inicio") LocalDate inicio,
                                        @Param("fim") LocalDate fim,
                                        @Param("tipoEnvio") String tipoEnvio,
                                        @Param("tipoComunicacao") String tipoComunicacao);
    }

    interface SolicitacaoDiarioRepository extends JpaRepository<MetricaSolicitacaoDiario, MetricaSolicitacaoDiario.Pk> {

        /**
         * Recalcula o rollup do dia a partir de {@code solicitacao_servicos} (bucket = data de
         * criacao da solicitacao-mae). {@code concluidas} = servicos com status {@code concluida};
         * {@code em_andamento} = pendente/atribuida/agendada/em_execucao. Cancelados nao entram.
         */
        @Modifying
        @Query(nativeQuery = true, value = """
            INSERT INTO metrics.solicitacoes_diario AS d
              (dia, tipo_servico_id, tipo_servico_nome, concluidas, em_andamento, atualizado_em)
            SELECT :dia, ss.tipo_servico_id, ts.nome,
                   count(*) FILTER (WHERE ss.status = 'concluida'),
                   count(*) FILTER (WHERE ss.status IN ('pendente','atribuida','agendada','em_execucao')),
                   now()
            FROM solicitacao_servicos ss
            JOIN solicitacoes   s  ON s.id  = ss.solicitacao_id
            JOIN tipos_servico  ts ON ts.id = ss.tipo_servico_id
            WHERE s.criado_em >= CAST(:dia AS timestamptz)
              AND s.criado_em <  CAST(CAST(:dia AS date) + 1 AS timestamptz)
            GROUP BY ss.tipo_servico_id, ts.nome
            ON CONFLICT (dia, tipo_servico_id) DO UPDATE SET
              tipo_servico_nome = EXCLUDED.tipo_servico_nome,
              concluidas = EXCLUDED.concluidas, em_andamento = EXCLUDED.em_andamento,
              atualizado_em = now()
            """)
        int rolarSolicitacoesDoDia(@Param("dia") LocalDate dia);

        @Query("""
            SELECT d FROM MetricaSolicitacaoDiario d
            WHERE d.dia BETWEEN :inicio AND :fim
            ORDER BY d.dia, d.tipoServicoNome
            """)
        List<MetricaSolicitacaoDiario> buscar(@Param("inicio") LocalDate inicio,
                                              @Param("fim") LocalDate fim);
    }
}
