package br.com.vistoria.core.modules.publico;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AcessoPublicoRepository extends JpaRepository<AcessoPublico, UUID> {
    Optional<AcessoPublico> findByTokenHashAndRevogadoFalse(String hash);
}
