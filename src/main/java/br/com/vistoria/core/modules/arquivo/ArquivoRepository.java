package br.com.vistoria.core.modules.arquivo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArquivoRepository extends JpaRepository<Arquivo, UUID> {
    List<Arquivo> findByOwnerTypeAndOwnerIdOrderByIdAsc(String ownerType, UUID ownerId);
    Optional<Arquivo> findByR2Key(String r2Key);
}
