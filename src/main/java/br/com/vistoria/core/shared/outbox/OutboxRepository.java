package br.com.vistoria.core.shared.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            SELECT * FROM outbox_eventos
            WHERE publicado_em IS NULL
            ORDER BY criado_em
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockPendingBatch();

    @Modifying
    @Query(value = """
            DELETE FROM outbox_eventos
            WHERE id IN (
                SELECT id FROM outbox_eventos
                WHERE publicado_em IS NOT NULL AND publicado_em < :cutoffTime
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int purgeBatch(@Param("cutoffTime") Instant cutoffTime, @Param("batchSize") int batchSize);
}
