package br.com.vistoria.core.modules.atribuicao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtribuicaoRepositories {
    interface AtribuicaoRepository extends JpaRepository<Atribuicao, UUID> {
        @Query("""
            SELECT a.vistoriadorId, COUNT(a), MAX(a.criadoEm)
            FROM Atribuicao a
            WHERE a.vistoriadorId IN :ids AND a.criadoEm >= :inicioMes
              AND a.acao IN ('atribuido','reatribuido')
            GROUP BY a.vistoriadorId
            """)
        List<Object[]> workloadMes(@Param("ids") List<UUID> ids, @Param("inicioMes") Instant inicioMes);
    }
    interface FilaRepository extends JpaRepository<FilaAtribuicao, UUID> {
        Optional<FilaAtribuicao> findByServicoId(UUID servicoId);
        List<FilaAtribuicao> findByProximaTentativaBefore(Instant now);
    }
}
