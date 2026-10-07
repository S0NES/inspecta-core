package br.com.vistoria.core.modules.cadastro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FotoRepository extends JpaRepository<Foto, UUID> {
    List<Foto> findByAmbienteIdOrderByOrdemAsc(UUID ambienteId);
}
