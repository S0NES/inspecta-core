package br.com.vistoria.core.modules.disponibilidade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DisponibilidadeRepositories {
    interface VistoriadorRepository extends JpaRepository<Vistoriador, UUID> {
        List<Vistoriador> findByAtivoTrue();
        java.util.Optional<Vistoriador> findByUsuarioId(UUID usuarioId);
    }
    interface DisponibilidadeRepository extends JpaRepository<Disponibilidade, UUID> {
        List<Disponibilidade> findByVistoriadorIdAndDiaSemana(UUID vistoriadorId, int diaSemana);
        // T2.3(b): DB-level tenant-scoped pagination — filters before paging, unlike the removed
        // post-pagination approach which broke page counts/ordering across tenant boundaries.
        Page<Disponibilidade> findByVistoriadorIdIn(List<UUID> vistoriadorIds, Pageable p);
        /** Grade semanal de um vistoriador para a UI de gestão. */
        List<Disponibilidade> findByVistoriadorIdOrderByDiaSemanaAscHoraInicioAsc(UUID vistoriadorId);
    }
    interface ExcecaoRepository extends JpaRepository<DisponibilidadeExcecao, UUID> {
        List<DisponibilidadeExcecao> findByVistoriadorIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
                UUID vistoriadorId, LocalDate d1, LocalDate d2);
        /** Todas as exceções (folgas/bloqueios) de um vistoriador para a UI. */
        List<DisponibilidadeExcecao> findByVistoriadorIdOrderByDataInicioAsc(UUID vistoriadorId);
    }
}
