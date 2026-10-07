package br.com.vistoria.core.modules.laudo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LaudoRepository extends JpaRepository<Laudo, UUID> {
    List<Laudo> findBySolicitacaoServicoIdOrderByVersaoDesc(UUID servicoId);
    Optional<Laudo> findFirstBySolicitacaoServicoIdOrderByVersaoDesc(UUID servicoId);
    List<Laudo> findBySolicitacaoServicoIdIn(Collection<UUID> servicoIds);
}
