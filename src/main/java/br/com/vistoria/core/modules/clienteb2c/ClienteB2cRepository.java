package br.com.vistoria.core.modules.clienteb2c;

import br.com.vistoria.core.modules.usuario.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ClienteB2cRepository extends JpaRepository<ClienteB2c, UUID> {
    Optional<ClienteB2c> findByUsuarioId(UUID usuarioId);
    boolean existsByEmail(String email);

    /**
     * Status (convidado/ativo/inativo) vive em {@link Usuario#getStatus()}, não em
     * ClienteB2c — join theta-style (JPQL) pelo usuarioId, sem relação mapeada.
     * {@code tem*} desliga o filtro correspondente.
     */
    @Query("""
            SELECT c FROM ClienteB2c c, Usuario u
            WHERE u.id = c.usuarioId
              AND (:temQ = false
                   OR LOWER(c.nome) LIKE :q
                   OR LOWER(c.email) LIKE :q)
              AND (:temCidade = false OR c.cidadeId = :cidadeId)
              AND (:temStatus = false OR u.status = :status)
            """)
    Page<ClienteB2c> search(@Param("temQ") boolean temQ,
                            @Param("q") String q,
                            @Param("temCidade") boolean temCidade,
                            @Param("cidadeId") UUID cidadeId,
                            @Param("temStatus") boolean temStatus,
                            @Param("status") Usuario.Status status,
                            Pageable pageable);
}
