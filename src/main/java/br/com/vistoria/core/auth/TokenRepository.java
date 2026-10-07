package br.com.vistoria.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TokenRepository extends JpaRepository<TokenEntity, UUID> {
    Optional<TokenEntity> findByHashAndTipo(String hash, TokenEntity.Tipo tipo);
    Optional<TokenEntity> findByPrefixAndTipoAndRevogadoFalse(String prefix, TokenEntity.Tipo tipo);
    List<TokenEntity> findByUsuarioIdAndTipoAndRevogadoFalse(UUID usuarioId, TokenEntity.Tipo tipo);
    List<TokenEntity> findByTipoAndRevogadoFalse(TokenEntity.Tipo tipo);
    /** Todas as chaves de um tipo (inclui revogadas) para a UI de administração. */
    List<TokenEntity> findByTipoOrderByCriadoEmDesc(TokenEntity.Tipo tipo);
    /** Chaves de um tipo por imobiliária (inclui revogadas) — lista filtrável da UI. */
    List<TokenEntity> findByTipoAndImobiliariaIdOrderByCriadoEmDesc(TokenEntity.Tipo tipo, UUID imobiliariaId);
}
