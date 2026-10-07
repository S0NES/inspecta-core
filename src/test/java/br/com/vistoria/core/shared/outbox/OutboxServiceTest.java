package br.com.vistoria.core.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxService")
class OutboxServiceTest {

    @Mock
    private OutboxRepository repository;

    private OutboxService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new OutboxService(repository, mapper);
    }

    @Test
    @DisplayName("publish serializes event and saves to repository")
    void publish_serializesAndSaves() {
        String routingKey = "domain.event";
        Map<String, Object> event = Map.of("eventId", "123", "action", "created");

        service.publish(routingKey, event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getRoutingKey()).isEqualTo(routingKey);
        assertThat(saved.getPayload()).contains("eventId").contains("123");
    }

    @Test
    @DisplayName("publish with nested object serializes correctly")
    void publish_withNestedObject() {
        String routingKey = "test.nested";
        Map<String, Object> nested = Map.of(
                "outer", Map.of("inner", "value", "count", 42)
        );

        service.publish(routingKey, nested);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getPayload())
                .contains("outer")
                .contains("inner")
                .contains("value")
                .contains("42");
    }

    @Test
    @DisplayName("publish with unserializable object throws IllegalArgumentException")
    void publish_unserializableThrows() {
        String routingKey = "bad.event";
        Object unserializable = new Object() {
            public Object getRecursive() {
                return this; // Infinite recursion
            }
        };

        assertThatThrownBy(() -> service.publish(routingKey, unserializable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unserializable");
    }

    @Test
    @DisplayName("publish with Map.of() event works")
    void publish_withMapOf() {
        service.publish("simple.event", Map.of("id", 1, "name", "test"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getPayload()).contains("id").contains("name");
    }

    @Test
    @DisplayName("publish annotation expects MANDATORY propagation")
    void publish_hasMandatoryPropagation() throws Exception {
        // This test verifies the annotation is present.
        // Real transaction semantics are tested in integration tests.
        java.lang.reflect.Method method = OutboxService.class.getDeclaredMethod(
                "publish", String.class, Object.class);
        org.springframework.transaction.annotation.Transactional tx =
                method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    @DisplayName("purgeOlder batches deletes of rows older than cutoff")
    void purgeOlder_batchesDeletes() {
        java.time.Instant cutoff = java.time.Instant.now().minus(14, java.time.temporal.ChronoUnit.DAYS);

        // First batch: 1000 rows deleted
        // Second batch: 1000 rows deleted
        // Third batch: 500 rows deleted (< batchSize, stops loop)
        org.mockito.Mockito.when(repository.purgeBatch(cutoff, 1000))
                .thenReturn(1000)
                .thenReturn(1000)
                .thenReturn(500);

        int total = service.purgeOlder(cutoff, 1000);

        assertThat(total).isEqualTo(2500);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(3))
                .purgeBatch(cutoff, 1000);
    }

    @Test
    @DisplayName("purgeOlder stops when batch returns fewer than batchSize")
    void purgeOlder_stopsWhenBatchSmaller() {
        java.time.Instant cutoff = java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);

        // First batch: 500 rows (less than batchSize 1000, so stop)
        org.mockito.Mockito.when(repository.purgeBatch(cutoff, 1000))
                .thenReturn(500);

        int total = service.purgeOlder(cutoff, 1000);

        assertThat(total).isEqualTo(500);
        org.mockito.Mockito.verify(repository).purgeBatch(cutoff, 1000);
    }

    @Test
    @DisplayName("purgeOlder returns 0 when no rows deleted")
    void purgeOlder_zeroRowsDeleted() {
        java.time.Instant cutoff = java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);

        org.mockito.Mockito.when(repository.purgeBatch(cutoff, 1000))
                .thenReturn(0);

        int total = service.purgeOlder(cutoff, 1000);

        assertThat(total).isEqualTo(0);
    }
}
