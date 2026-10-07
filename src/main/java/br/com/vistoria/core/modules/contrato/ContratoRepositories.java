package br.com.vistoria.core.modules.contrato;

import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContratoRepositories {
    interface ContratoRepository extends JpaRepository<Contrato, UUID> {
        Optional<Contrato> findFirstByImobiliariaIdAndStatus(UUID imobiliariaId, String status);
        List<Contrato> findByImobiliariaIdOrderByCriadoEmDesc(UUID imobiliariaId);

        /** Contratos com dado status para um lote de imobiliárias — evita N+1 em relatórios. */
        List<Contrato> findByImobiliariaIdInAndStatus(Collection<UUID> imobiliariaIds, String status);
    }
    interface FaixaRepository extends JpaRepository<ContratoFaixa, UUID> {
        // V4 — modo faixa re-chaveado por Serviço guarda-chuva (decisão 1-a).
        List<ContratoFaixa> findByContratoIdAndServicoId(UUID contratoId, UUID servicoId);
        Optional<ContratoFaixa> findFirstByContratoIdAndServicoIdAndFaixaReferenciaId(
                UUID contratoId, UUID servicoId, Short faixaReferenciaId);

        // Legado (inspecta-api /v1/contratos): resolução por tipo_servico_id + metragem.
        List<ContratoFaixa> findByContratoIdAndTipoServicoId(UUID contratoId, UUID tipoServicoId);
        List<ContratoFaixa> findByContratoIdOrderByTipoServicoIdAscMetragemMinAsc(UUID contratoId);
        Optional<ContratoFaixa> findFirstByContratoIdAndTipoServicoIdAndMetragemMinLessThanEqualAndMetragemMaxGreaterThanEqual(
                UUID contratoId, UUID tipoServicoId, BigDecimal m1, BigDecimal m2);
    }
    interface RepasseRepository extends JpaRepository<ContratoRepasse, UUID> {
        Optional<ContratoRepasse> findFirstByContratoFaixaId(UUID faixaId);
    }
    /** V3 — Serviços guarda-chuva oferecidos no contrato + valores percentuais por serviço. */
    interface ContratoServicoGrupoRepository extends JpaRepository<ContratoServicoGrupo, UUID> {
        List<ContratoServicoGrupo> findByContratoId(UUID contratoId);
        Optional<ContratoServicoGrupo> findFirstByContratoIdAndServicoId(UUID contratoId, UUID servicoId);
        void deleteByContratoId(UUID contratoId);
    }
    interface ServicoRepository extends JpaRepository<ContratoServico, UUID> {
        Optional<ContratoServico> findFirstByContratoIdAndTipoServicoId(UUID contratoId, UUID tipoServicoId);
        List<ContratoServico> findByContratoId(UUID contratoId);

        /** Imobiliárias que cobrem um tipo de serviço num contrato ativo (via contrato_servicos). */
        @org.springframework.data.jpa.repository.Query("""
            select distinct c.imobiliariaId
            from ContratoServico cs, Contrato c
            where cs.contratoId = c.id and cs.tipoServicoId = :tipoServicoId and cs.ativo = true
            """)
        List<UUID> findImobiliariaIdsByTipoServicoId(@org.springframework.data.repository.query.Param("tipoServicoId") UUID tipoServicoId);
    }
}
