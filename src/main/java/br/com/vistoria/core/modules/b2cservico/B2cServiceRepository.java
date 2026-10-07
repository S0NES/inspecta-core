package br.com.vistoria.core.modules.b2cservico;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface B2cServiceRepository extends JpaRepository<B2cService, UUID> {
    List<B2cService> findAllByOrderByOrdemAscNomeAsc();
    List<B2cService> findAllByAtivoTrueOrderByOrdemAscNomeAsc();
}
