package br.com.vistoria.core.modules.cadastro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AmbienteRepository extends JpaRepository<Ambiente, UUID> {
    List<Ambiente> findByImovelIdOrderByOrdemAsc(UUID imovelId);
}
