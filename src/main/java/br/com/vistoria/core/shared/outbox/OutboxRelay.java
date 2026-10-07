package br.com.vistoria.core.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "inspecta.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxRepository repository;
    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${inspecta.outbox.poll-ms:2000}")
    @Transactional
    public void publishPending() {
        var batch = repository.lockPendingBatch();
        for (OutboxEvent event : batch) {
            try {
                // Parse JSON string to object to avoid double-encoding on the wire.
                // Jackson2JsonMessageConverter will serialize the parsed object,
                // resulting in clean JSON matching eventos.md envelope spec (not a JSON-encoded string).
                var payloadObject = objectMapper.readTree(event.getPayload());
                rabbit.convertAndSend(event.getRoutingKey(), payloadObject);
                event.setPublicadoEm(Instant.now());
            } catch (Exception ex) {
                event.setTentativas(event.getTentativas() + 1);
                log.error("Outbox publish failed id={} rk={} tentativa={}",
                        event.getId(), event.getRoutingKey(), event.getTentativas(), ex);
            }
        }
    }
}
