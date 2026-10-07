package br.com.vistoria.core.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String routingKey, Object event) {
        try {
            repository.save(OutboxEvent.of(routingKey, mapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unserializable event for " + routingKey, e);
        }
    }

    @Transactional
    public int purgeOlder(Instant cutoffTime, int batchSize) {
        int totalDeleted = 0;
        int deleted;
        do {
            deleted = repository.purgeBatch(cutoffTime, batchSize);
            totalDeleted += deleted;
            log.debug("Outbox retention batch deleted={} total={}", deleted, totalDeleted);
        } while (deleted >= batchSize);
        if (totalDeleted > 0) {
            log.info("Outbox retention complete: deleted {} rows older than {}", totalDeleted, cutoffTime);
        }
        return totalDeleted;
    }
}
