package br.com.vistoria.core.modules.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinanceiroRepositories {
    interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {
        Optional<Pagamento> findFirstBySolicitacaoIdOrderByCriadoEmDesc(UUID solicitacaoId);
        Optional<Pagamento> findByExternalId(String externalId);
        Optional<Pagamento> findByPixId(String pixId);

        /** PIX ainda não confirmado — alvo da reconciliação periódica no provedor. */
        java.util.List<Pagamento> findByStatusAndPixIdIsNotNullAndPixExpiraEmAfter(String status, java.time.Instant apos);

        /** Projeção de consumo pago por imobiliária no período. */
        interface ConsumoRow {
            UUID getImobiliariaId();
            long getServicosPagos();
            BigDecimal getValorTotal();
        }

        /**
         * Consumo (pagamentos com status 'pago') agregado por imobiliária num intervalo.
         * Solicitação sem imobiliária (dado legado) fica de fora.
         */
        // inicio/fim vem sempre de Periodo.range(...) (nunca null). Um ramo `:inicio IS NULL`
        // com bind sem tipo fazia o PostgreSQL estourar 42P18 "could not determine data type of
        // parameter $1" — passava em teste (mock/H2), quebrava em prod (RELATORIO.md achado #2).
        @Query("""
            SELECT s.imobiliariaId AS imobiliariaId,
                   COUNT(p) AS servicosPagos,
                   COALESCE(SUM(p.valor), 0) AS valorTotal
            FROM Pagamento p, br.com.vistoria.core.modules.solicitacao.Solicitacao s
            WHERE s.id = p.solicitacaoId
              AND p.status = 'pago'
              AND s.imobiliariaId IS NOT NULL
              AND p.criadoEm >= :inicio
              AND p.criadoEm < :fim
            GROUP BY s.imobiliariaId
            """)
        List<ConsumoRow> consumoPorImobiliaria(@Param("inicio") Instant inicio, @Param("fim") Instant fim);

        /** Cobranças ainda em aberto (criando/pendente) por imobiliária no período. */
        interface PendenteRow {
            UUID getImobiliariaId();
            long getPagamentosPendentes();
            BigDecimal getValorPendente();
        }

        @Query("""
            SELECT s.imobiliariaId AS imobiliariaId,
                   COUNT(p) AS pagamentosPendentes,
                   COALESCE(SUM(p.valor), 0) AS valorPendente
            FROM Pagamento p, br.com.vistoria.core.modules.solicitacao.Solicitacao s
            WHERE s.id = p.solicitacaoId
              AND p.status IN ('criando', 'pendente')
              AND s.imobiliariaId IS NOT NULL
              AND p.criadoEm >= :inicio
              AND p.criadoEm < :fim
            GROUP BY s.imobiliariaId
            """)
        List<PendenteRow> pendentePorImobiliaria(@Param("inicio") Instant inicio, @Param("fim") Instant fim);

        @Query("""
            SELECT p FROM Pagamento p
            WHERE (:temStatus = false OR p.status = :status)
              AND (:temQ = false OR LOWER(COALESCE(p.externalId, '')) LIKE :q
                   OR LOWER(COALESCE(p.provider, '')) LIKE :q
                   OR LOWER(COALESCE(p.checkoutUrl, '')) LIKE :q)
            ORDER BY p.criadoEm DESC
            """)
        org.springframework.data.domain.Page<Pagamento> painel(
                @Param("temStatus") boolean temStatus,
                @Param("status") String status,
                @Param("temQ") boolean temQ,
                @Param("q") String q,
                org.springframework.data.domain.Pageable pageable);
    }
    interface PagamentoVistoriadorRepository extends JpaRepository<PagamentoVistoriador, UUID> {
        List<PagamentoVistoriador> findByVistoriadorIdAndPeriodo(UUID vistoriadorId, String periodo);
    }

    interface ExtratoItemRepository extends JpaRepository<ExtratoItem, UUID> {

        /**
         * Itens em aberto (sem fatura) de uma imobiliária no intervalo [inicio, fim).
         * {@code PESSIMISTIC_WRITE} serializa dois POST /extrato/faturas concorrentes
         * (mesmo recorte ou janelas sobrepostas) até o unique de período no INSERT.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
            SELECT ei FROM ExtratoItem ei
            WHERE ei.imobiliariaId = :imobiliariaId
              AND ei.faturaId IS NULL
              AND ei.criadoEm >= :inicio AND ei.criadoEm < :fim
            ORDER BY ei.criadoEm DESC
            """)
        List<ExtratoItem> findAbertos(@Param("imobiliariaId") UUID imobiliariaId,
                                      @Param("inicio") Instant inicio, @Param("fim") Instant fim);

        /**
         * Itens em aberto no período com o join para a UI de Extrato (endereço do imóvel,
         * tipo de serviço, data). Entity joins ad-hoc (sem associação mapeada).
         */
        @Query("""
            SELECT new br.com.vistoria.core.modules.financeiro.ExtratoItemAberto(
                ei.id, ei.solicitacaoServicoId, s.id, ei.valor, ei.criadoEm,
                i.endereco, i.numero, ts.nome)
            FROM ExtratoItem ei
              JOIN br.com.vistoria.core.modules.solicitacao.SolicitacaoServico ss ON ss.id = ei.solicitacaoServicoId
              JOIN br.com.vistoria.core.modules.solicitacao.Solicitacao s ON s.id = ss.solicitacaoId
              JOIN br.com.vistoria.core.modules.cadastro.Imovel i ON i.id = s.imovelId
              JOIN br.com.vistoria.core.modules.cadastro.TipoServico ts ON ts.id = ss.tipoServicoId
            WHERE ei.imobiliariaId = :imobiliariaId
              AND ei.faturaId IS NULL
              AND ei.criadoEm >= :inicio AND ei.criadoEm < :fim
            ORDER BY ei.criadoEm DESC
            """)
        List<ExtratoItemAberto> listarAbertosDetalhado(@Param("imobiliariaId") UUID imobiliariaId,
                                                       @Param("inicio") Instant inicio, @Param("fim") Instant fim);

        List<ExtratoItem> findByFaturaId(UUID faturaId);
    }

    interface FaturaConsolidadaRepository extends JpaRepository<FaturaConsolidada, UUID> {
        List<FaturaConsolidada> findByImobiliariaIdOrderByCriadoEmDesc(UUID imobiliariaId);
        java.util.Optional<FaturaConsolidada> findByCheckoutId(String checkoutId);
    }
}
