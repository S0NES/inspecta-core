package br.com.vistoria.core.modules.integracao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegracaoSyncRepository extends JpaRepository<IntegracaoSync, UUID> {
    Optional<IntegracaoSync> findBySistemaAndRecursoTipoAndRecursoId(String sistema, String recursoTipo, UUID recursoId);
    List<IntegracaoSync> findTop50ByStatusInOrderByIdAsc(List<String> status);

    /** Painel de integração (admin): linhas de um sistema, mais recentes primeiro. */
    Page<IntegracaoSync> findBySistemaOrderByIdDesc(String sistema, Pageable pageable);
    Page<IntegracaoSync> findBySistemaAndStatusOrderByIdDesc(String sistema, String status, Pageable pageable);

    @Query(value = """
            SELECT s.* FROM integracao_sync s
            WHERE s.sistema = :sistema
              AND (:temStatus = false OR s.status = :status)
              AND (:temQ = false OR (
                    LOWER(s.recurso_tipo) LIKE :q
                 OR LOWER(COALESCE(s.external_id, '')) LIKE :q
                 OR LOWER(COALESCE(s.erro, '')) LIKE :q
                 OR LOWER(COALESCE((
                      CASE s.recurso_tipo
                        WHEN 'imobiliaria' THEN (
                          SELECT COALESCE(NULLIF(i.nome_fantasia, ''), i.razao_social)
                          FROM imobiliarias i WHERE i.id = s.recurso_id)
                        WHEN 'imovel' THEN (
                          SELECT im.endereco FROM imoveis im WHERE im.id = s.recurso_id)
                        WHEN 'solicitacao' THEN (
                          SELECT im.endereco FROM solicitacoes sol
                          JOIN imoveis im ON im.id = sol.imovel_id
                          WHERE sol.id = s.recurso_id)
                        WHEN 'vistoriador' THEN (
                          SELECT u.nome FROM vistoriadores v
                          JOIN usuarios u ON u.id = v.usuario_id
                          WHERE v.id = s.recurso_id)
                        ELSE ''
                      END), '')) LIKE :q
              ))
            ORDER BY s.id DESC
            """,
            countQuery = """
            SELECT count(*) FROM integracao_sync s
            WHERE s.sistema = :sistema
              AND (:temStatus = false OR s.status = :status)
              AND (:temQ = false OR (
                    LOWER(s.recurso_tipo) LIKE :q
                 OR LOWER(COALESCE(s.external_id, '')) LIKE :q
                 OR LOWER(COALESCE(s.erro, '')) LIKE :q
                 OR LOWER(COALESCE((
                      CASE s.recurso_tipo
                        WHEN 'imobiliaria' THEN (
                          SELECT COALESCE(NULLIF(i.nome_fantasia, ''), i.razao_social)
                          FROM imobiliarias i WHERE i.id = s.recurso_id)
                        WHEN 'imovel' THEN (
                          SELECT im.endereco FROM imoveis im WHERE im.id = s.recurso_id)
                        WHEN 'solicitacao' THEN (
                          SELECT im.endereco FROM solicitacoes sol
                          JOIN imoveis im ON im.id = sol.imovel_id
                          WHERE sol.id = s.recurso_id)
                        WHEN 'vistoriador' THEN (
                          SELECT u.nome FROM vistoriadores v
                          JOIN usuarios u ON u.id = v.usuario_id
                          WHERE v.id = s.recurso_id)
                        ELSE ''
                      END), '')) LIKE :q
              ))
            """,
            nativeQuery = true)
    Page<IntegracaoSync> buscarPainel(
            @Param("sistema") String sistema,
            @Param("temStatus") boolean temStatus,
            @Param("status") String status,
            @Param("temQ") boolean temQ,
            @Param("q") String q,
            Pageable pageable);
}
