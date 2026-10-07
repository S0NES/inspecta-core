package br.com.vistoria.core.modules.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface UsuarioImobiliariaRepository
        extends JpaRepository<UsuarioImobiliaria, UsuarioImobiliaria.Pk> {

    @Query("select ui.imobiliariaId from UsuarioImobiliaria ui where ui.usuarioId = :usuarioId")
    List<UUID> findImobiliariaIdsByUsuarioId(@Param("usuarioId") UUID usuarioId);

    @Transactional
    void deleteByUsuarioIdAndImobiliariaId(UUID usuarioId, UUID imobiliariaId);

    @Transactional
    void deleteByUsuarioId(UUID usuarioId);
}
