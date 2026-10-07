package br.com.vistoria.core.modules.usuario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Lista paginada (ADMIN/SUPORTE, sem tenant). {@code tem*} desliga o filtro correspondente;
     * filtrar em memória só enxergaria a página corrente.
     */
    @Query("""
            SELECT u FROM Usuario u
            WHERE (:temPerfil = false OR u.perfil = :perfil)
              AND (:temStatus = false OR u.status = :status)
              AND (:temQ = false
                   OR LOWER(u.nome) LIKE :q
                   OR LOWER(u.email) LIKE :q)
            """)
    Page<Usuario> search(@Param("temPerfil") boolean temPerfil,
                         @Param("perfil") Usuario.Perfil perfil,
                         @Param("temStatus") boolean temStatus,
                         @Param("status") Usuario.Status status,
                         @Param("temQ") boolean temQ,
                         @Param("q") String q,
                         Pageable pageable);

    /**
     * IMOB_GERENCIA (tenant-scoped, T2.3): usuários vinculados a alguma das imobiliárias do
     * chamador — pelo vínculo primário ({@code imobiliaria_id}) ou pelos múltiplos vínculos
     * (V25, {@code usuario_imobiliarias}) — restritos aos perfis informados.
     */
    @Query("""
            SELECT u FROM Usuario u
            WHERE (u.imobiliariaId IN :tenants
                OR u.id IN (SELECT ui.usuarioId FROM UsuarioImobiliaria ui WHERE ui.imobiliariaId IN :tenants))
              AND u.perfil IN :perfis
              AND (:temStatus = false OR u.status = :status)
              AND (:temQ = false
                   OR LOWER(u.nome) LIKE :q
                   OR LOWER(u.email) LIKE :q)
            """)
    Page<Usuario> searchByTenantsAndPerfilIn(
            @Param("tenants") List<UUID> tenants,
            @Param("perfis") List<Usuario.Perfil> perfis,
            @Param("temStatus") boolean temStatus,
            @Param("status") Usuario.Status status,
            @Param("temQ") boolean temQ,
            @Param("q") String q,
            Pageable pageable);
}
