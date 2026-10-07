package br.com.vistoria.core.modules.solicitacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SolicitacaoRepositories {
    interface SolicitacaoRepository extends JpaRepository<Solicitacao, UUID> {
        Page<Solicitacao> findByClienteId(UUID clienteId, Pageable p);

        @Query("""
            SELECT DISTINCT s FROM Solicitacao s
            WHERE s.id IN (SELECT ss.solicitacaoId FROM SolicitacaoServico ss
                          WHERE ss.vistoriadorId = :vistoriadorId)
            """)
        Page<Solicitacao> findByVistoriadorId(@Param("vistoriadorId") UUID vistoriadorId, Pageable p);

        /**
         * Busca com filtros aplicados no banco. Cada parâmetro nulo desliga o próprio filtro.
         *
         * <p>O filtro de {@code statusKey} reproduz em SQL o status derivado dos serviços (a mesma
         * regra de {@code SolicitacaoService.statusDerivado}); filtrar em memória só enxergaria a
         * página corrente e daria contagens erradas.</p>
         *
         * <p>{@code vistoriadorId} e {@code clienteId} carregam o row scoping para dentro da
         * mesma consulta, de modo que a paginação continue correta para esses perfis.</p>
         */
        @Query("""
            SELECT s FROM Solicitacao s, Imovel i
            WHERE i.id = s.imovelId
              AND (:q IS NULL
                   OR LOWER(i.endereco) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR CAST(s.id AS string) LIKE CONCAT('%', CAST(:q AS string), '%'))
              AND (:cidadeId IS NULL OR i.cidadeId = :cidadeId)
              AND (:urgente IS NULL OR s.urgente = :urgente)
              AND (:imobiliariaId IS NULL OR s.imobiliariaId = :imobiliariaId)
              AND (:clienteId IS NULL OR s.clienteId = :clienteId)
              AND (:vistoriadorId IS NULL OR EXISTS (
                    SELECT 1 FROM SolicitacaoServico v
                    WHERE v.solicitacaoId = s.id AND v.vistoriadorId = :vistoriadorId))
              AND (:statusKey IS NULL
                   OR (:statusKey = 'cancelada'
                       AND EXISTS (SELECT 1 FROM SolicitacaoServico x WHERE x.solicitacaoId = s.id)
                       AND NOT EXISTS (SELECT 1 FROM SolicitacaoServico x
                                       WHERE x.solicitacaoId = s.id AND x.status <> 'cancelada'))
                   OR (:statusKey = 'concluida'
                       AND EXISTS (SELECT 1 FROM SolicitacaoServico x WHERE x.solicitacaoId = s.id)
                       AND NOT EXISTS (SELECT 1 FROM SolicitacaoServico x
                                       WHERE x.solicitacaoId = s.id AND x.status <> 'concluida'))
                   OR (:statusKey = 'em_campo'
                       AND EXISTS (SELECT 1 FROM SolicitacaoServico x
                                   WHERE x.solicitacaoId = s.id AND x.status = 'em_execucao'))
                   OR (:statusKey = 'agendada'
                       AND EXISTS (SELECT 1 FROM SolicitacaoServico x
                                   WHERE x.solicitacaoId = s.id AND x.status = 'agendada')
                       AND NOT EXISTS (SELECT 1 FROM SolicitacaoServico x
                                       WHERE x.solicitacaoId = s.id AND x.status = 'em_execucao'))
                   OR (:statusKey = 'atribuida'
                       AND EXISTS (SELECT 1 FROM SolicitacaoServico x
                                   WHERE x.solicitacaoId = s.id AND x.status = 'atribuida')
                       AND NOT EXISTS (SELECT 1 FROM SolicitacaoServico x
                                       WHERE x.solicitacaoId = s.id
                                         AND x.status IN ('em_execucao', 'agendada')))
                   OR (:statusKey = 'solicitada'
                       AND NOT EXISTS (SELECT 1 FROM SolicitacaoServico x
                                       WHERE x.solicitacaoId = s.id
                                         AND x.status <> 'pendente')))
            """)
        Page<Solicitacao> buscar(@Param("q") String q,
                                 @Param("cidadeId") UUID cidadeId,
                                 @Param("urgente") Boolean urgente,
                                 @Param("statusKey") String statusKey,
                                 @Param("imobiliariaId") UUID imobiliariaId,
                                 @Param("clienteId") UUID clienteId,
                                 @Param("vistoriadorId") UUID vistoriadorId,
                                 Pageable p);
    }

    interface SolicitacaoIntervenienteRepository extends JpaRepository<SolicitacaoInterveniente, UUID> {
        List<SolicitacaoInterveniente> findBySolicitacaoId(UUID solicitacaoId);
        void deleteBySolicitacaoId(UUID solicitacaoId);
    }

    interface ServicoRepository extends JpaRepository<SolicitacaoServico, UUID> {
        List<SolicitacaoServico> findBySolicitacaoId(UUID solicitacaoId);

        @Query("""
            SELECT s FROM SolicitacaoServico s
            WHERE s.vistoriadorId = :vistoriadorId
              AND s.status IN ('atribuida','agendada','em_execucao')
              AND s.dataAgendada BETWEEN :inicio AND :fim
            """)
        List<SolicitacaoServico> agendaOcupada(@Param("vistoriadorId") UUID vistoriadorId,
                                               @Param("inicio") Instant inicio,
                                               @Param("fim") Instant fim);

        @Query("""
            SELECT s FROM SolicitacaoServico s
            WHERE s.vistoriadorId = :vistoriadorId
              AND s.status = 'concluida'
              AND s.dataAgendada BETWEEN :inicio AND :fim
            """)
        List<SolicitacaoServico> concluidosNoPeriodo(@Param("vistoriadorId") UUID vistoriadorId,
                                                      @Param("inicio") Instant inicio,
                                                      @Param("fim") Instant fim);

        @Query("""
            SELECT s FROM SolicitacaoServico s
            WHERE s.solicitacaoId IN (SELECT sol.id FROM Solicitacao sol WHERE sol.imobiliariaId = :imobiliariaId)
              AND s.criadoEm BETWEEN :inicio AND :fim
              AND s.status <> 'cancelada'
            """)
        List<SolicitacaoServico> faturamentoNoPeriodo(@Param("imobiliariaId") UUID imobiliariaId,
                                                       @Param("inicio") Instant inicio,
                                                       @Param("fim") Instant fim);

        @Query("""
            SELECT DISTINCT s.vistoriadorId FROM SolicitacaoServico s
            WHERE s.solicitacaoId IN (SELECT sol.id FROM Solicitacao sol WHERE sol.imobiliariaId = :imobiliariaId)
              AND s.vistoriadorId IS NOT NULL
            """)
        List<UUID> findDistinctVistoriadorIdsByImobiliaria(@Param("imobiliariaId") UUID imobiliariaId);

        /** Igual a {@link #findDistinctVistoriadorIdsByImobiliaria} para usuários vinculados a mais
         *  de uma imobiliária (V25 — TenantContext.getAll()). Lista vazia devolve vazio. */
        @Query("""
            SELECT DISTINCT s.vistoriadorId FROM SolicitacaoServico s
            WHERE s.solicitacaoId IN (SELECT sol.id FROM Solicitacao sol WHERE sol.imobiliariaId IN :imobiliariaIds)
              AND s.vistoriadorId IS NOT NULL
            """)
        List<UUID> findDistinctVistoriadorIdsByImobiliariaIn(@Param("imobiliariaIds") List<UUID> imobiliariaIds);

        /**
         * Extrato granular (uma linha por serviço). Booleanos nunca-nulos evitam o 42P18
         * do PostgreSQL em {@code :param IS NULL} com bind sem tipo.
         */
        @Query("""
            SELECT new br.com.vistoria.core.modules.financeiro.VistoriaExtratoLinha(
                ss.id, s.id, s.imobiliariaId,
                COALESCE(imob.nomeFantasia, imob.razaoSocial),
                i.endereco, ts.nome, u.nome, ss.status, ss.criadoEm, ss.precoSnapshot)
            FROM SolicitacaoServico ss
              JOIN br.com.vistoria.core.modules.solicitacao.Solicitacao s ON s.id = ss.solicitacaoId
              JOIN br.com.vistoria.core.modules.cadastro.Imovel i ON i.id = s.imovelId
              JOIN br.com.vistoria.core.modules.cadastro.TipoServico ts ON ts.id = ss.tipoServicoId
              LEFT JOIN br.com.vistoria.core.modules.imobiliaria.Imobiliaria imob ON imob.id = s.imobiliariaId
              LEFT JOIN br.com.vistoria.core.modules.disponibilidade.Vistoriador v ON v.id = ss.vistoriadorId
              LEFT JOIN br.com.vistoria.core.modules.usuario.Usuario u ON u.id = v.usuarioId
            WHERE ss.criadoEm >= :inicio AND ss.criadoEm < :fim
              AND (:temImob = false OR s.imobiliariaId = :imobiliariaId)
              AND (:temNome = false
                   OR LOWER(i.endereco) LIKE :nome
                   OR LOWER(COALESCE(imob.nomeFantasia, '')) LIKE :nome
                   OR LOWER(COALESCE(imob.razaoSocial, '')) LIKE :nome
                   OR LOWER(CAST(s.id AS string)) LIKE :nome)
            ORDER BY ss.criadoEm DESC
            """)
        org.springframework.data.domain.Page<br.com.vistoria.core.modules.financeiro.VistoriaExtratoLinha> listarVistorias(
                @Param("inicio") Instant inicio,
                @Param("fim") Instant fim,
                @Param("temImob") boolean temImob,
                @Param("imobiliariaId") UUID imobiliariaId,
                @Param("temNome") boolean temNome,
                @Param("nome") String nome,
                org.springframework.data.domain.Pageable pageable);

        @Query("""
            SELECT COALESCE(SUM(ss.precoSnapshot), 0)
            FROM SolicitacaoServico ss
              JOIN br.com.vistoria.core.modules.solicitacao.Solicitacao s ON s.id = ss.solicitacaoId
              JOIN br.com.vistoria.core.modules.cadastro.Imovel i ON i.id = s.imovelId
              LEFT JOIN br.com.vistoria.core.modules.imobiliaria.Imobiliaria imob ON imob.id = s.imobiliariaId
            WHERE ss.criadoEm >= :inicio AND ss.criadoEm < :fim
              AND (:temImob = false OR s.imobiliariaId = :imobiliariaId)
              AND (:temNome = false
                   OR LOWER(i.endereco) LIKE :nome
                   OR LOWER(COALESCE(imob.nomeFantasia, '')) LIKE :nome
                   OR LOWER(COALESCE(imob.razaoSocial, '')) LIKE :nome
                   OR LOWER(CAST(s.id AS string)) LIKE :nome)
            """)
        java.math.BigDecimal somarVistorias(
                @Param("inicio") Instant inicio,
                @Param("fim") Instant fim,
                @Param("temImob") boolean temImob,
                @Param("imobiliariaId") UUID imobiliariaId,
                @Param("temNome") boolean temNome,
                @Param("nome") String nome);
    }
}
