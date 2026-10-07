package br.com.vistoria.core.shared.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface WebhookRecebidoRepository extends JpaRepository<WebhookRecebido, UUID> {

    /**
     * Insere o webhook só se o {@code event_id} ainda não existe. Devolve 1 se inseriu, 0 se era
     * duplicado. Usa {@code ON CONFLICT DO NOTHING} (e não um {@code saveAndFlush} com catch de
     * {@code DataIntegrityViolationException}) para NÃO marcar a transação do chamador como
     * rollback-only — senão o commit lançava {@code UnexpectedRollbackException} e o endpoint
     * respondia 500 justamente no reenvio do provedor, que é o caso que o dedup existe para tratar.
     */
    @Modifying
    @Query(value = """
            INSERT INTO webhooks_recebidos (id, origem, event_id, payload)
            VALUES (gen_random_uuid(), :origem, :eventId, CAST(:payload AS jsonb))
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int inserirSeNovo(@Param("origem") String origem,
                      @Param("eventId") String eventId,
                      @Param("payload") String payload);
}
