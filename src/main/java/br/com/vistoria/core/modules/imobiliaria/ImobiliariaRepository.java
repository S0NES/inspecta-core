package br.com.vistoria.core.modules.imobiliaria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface ImobiliariaRepository extends JpaRepository<Imobiliaria, UUID> {
    boolean existsByCnpj(String cnpj);
    Page<Imobiliaria> findAllByIdIn(Collection<UUID> ids, Pageable pageable);
}
