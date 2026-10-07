package br.com.vistoria.core.modules.integracao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlenoPessoaGarantiaRepository extends JpaRepository<PlenoPessoaGarantia, UUID> {
    Optional<PlenoPessoaGarantia> findByImobiliariaId(UUID imobiliariaId);
}
