package br.com.vistoria.core.shared.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelay")
class OutboxRelayTest {

    @Mock
    private OutboxRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        relay = new OutboxRelay(repository, rabbitTemplate, objectMapper);
    }

    @Test
    @DisplayName("publishPending locks batch from repository")
    void publishPending_locksFromRepository() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setRoutingKey("test");
        event.setPayload("{}");

        when(repository.lockPendingBatch()).thenReturn(List.of(event));

        relay.publishPending();

        verify(repository).lockPendingBatch();
    }

    @Test
    @DisplayName("publishPending sets publicadoEm timestamp on success")
    void publishPending_setTimestamp() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setRoutingKey("test.event");
        event.setPayload("{\"test\": true}");
        event.setPublicadoEm(null);

        Instant beforePublish = Instant.now();
        when(repository.lockPendingBatch()).thenReturn(List.of(event));

        relay.publishPending();

        Instant afterPublish = Instant.now();

        assertThat(event.getPublicadoEm()).isBetween(beforePublish, afterPublish.plusSeconds(1));
    }

    @Test
    @DisplayName("publishPending handles multiple events")
    void publishPending_multipleEvents() {
        OutboxEvent event1 = new OutboxEvent();
        event1.setId(UUID.randomUUID());
        event1.setRoutingKey("event.one");
        event1.setPayload("{\"id\": 1}");

        OutboxEvent event2 = new OutboxEvent();
        event2.setId(UUID.randomUUID());
        event2.setRoutingKey("event.two");
        event2.setPayload("{\"id\": 2}");

        when(repository.lockPendingBatch()).thenReturn(List.of(event1, event2));

        relay.publishPending();

        verify(repository).lockPendingBatch();
    }

    @Test
    @DisplayName("publishPending with no pending events")
    void publishPending_noPendingEvents() {
        when(repository.lockPendingBatch()).thenReturn(List.of());

        relay.publishPending();

        verify(repository).lockPendingBatch();
    }

    @Test
    @DisplayName("publishPending increments tentativas on JSON parse failure")
    void publishPending_incrementsTentativasOnFailure() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setRoutingKey("failing.event");
        event.setPayload("{invalid json}");
        event.setTentativas(2);

        when(repository.lockPendingBatch()).thenReturn(List.of(event));

        relay.publishPending();

        // Invalid JSON should increment tentativas
        assertThat(event.getTentativas()).isEqualTo(3);
    }

    @Test
    @DisplayName("publishPending does not set timestamp on parse failure")
    void publishPending_noTimestampOnFailure() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setRoutingKey("test");
        event.setPayload("{invalid}");
        event.setPublicadoEm(null);

        when(repository.lockPendingBatch()).thenReturn(List.of(event));

        relay.publishPending();

        assertThat(event.getPublicadoEm()).isNull();
    }

    @Test
    @DisplayName("publishPending parses valid JSON payload")
    void publishPending_parsesJsonPayload() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setRoutingKey("json.test");
        event.setPayload("{\"name\": \"test\", \"value\": 42}");
        event.setPublicadoEm(null);

        when(repository.lockPendingBatch()).thenReturn(List.of(event));

        relay.publishPending();

        // If JSON is valid, timestamp should be set
        assertThat(event.getPublicadoEm()).isNotNull();
        assertThat(event.getTentativas()).isZero();
    }

    @Test
    @DisplayName("publishPending with complex JSON structure")
    void publishPending_complexJson() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setRoutingKey("complex.event");
        event.setPayload("{\"order\": {\"id\": 123, \"items\": [{\"name\": \"item1\"}, {\"name\": \"item2\"}]}}");
        event.setPublicadoEm(null);

        when(repository.lockPendingBatch()).thenReturn(List.of(event));

        relay.publishPending();

        assertThat(event.getPublicadoEm()).isNotNull();
    }

    @Test
    @DisplayName("publishPending with array JSON")
    void publishPending_arrayJson() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setRoutingKey("array.event");
        event.setPayload("[{\"id\": 1}, {\"id\": 2}]");
        event.setPublicadoEm(null);

        when(repository.lockPendingBatch()).thenReturn(List.of(event));

        relay.publishPending();

        assertThat(event.getPublicadoEm()).isNotNull();
    }

    @Test
    @DisplayName("publishPending with large batch")
    void publishPending_largeBatch() {
        List<OutboxEvent> events = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            OutboxEvent event = new OutboxEvent();
            event.setId(UUID.randomUUID());
            event.setRoutingKey("event." + i);
            event.setPayload("{\"index\": " + i + "}");
            events.add(event);
        }

        when(repository.lockPendingBatch()).thenReturn(events);

        relay.publishPending();

        verify(repository).lockPendingBatch();
    }

    @Test
    @DisplayName("publishPending with null payload")
    void publishPending_nullPayload() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setRoutingKey("test");
        event.setPayload(null);
        event.setTentativas(0);

        when(repository.lockPendingBatch()).thenReturn(List.of(event));

        relay.publishPending();

        // Null payload should increment tentativas
        assertThat(event.getTentativas()).isGreaterThan(0);
    }

    @Test
    @DisplayName("publishPending continues processing after one event fails")
    void publishPending_continuesAfterFailure() {
        OutboxEvent event1 = new OutboxEvent();
        event1.setId(UUID.randomUUID());
        event1.setRoutingKey("event.one");
        event1.setPayload("{invalid json}");
        event1.setTentativas(0);
        event1.setPublicadoEm(null);

        OutboxEvent event2 = new OutboxEvent();
        event2.setId(UUID.randomUUID());
        event2.setRoutingKey("event.two");
        event2.setPayload("{\"valid\": true}");
        event2.setTentativas(0);
        event2.setPublicadoEm(null);

        when(repository.lockPendingBatch()).thenReturn(List.of(event1, event2));

        relay.publishPending();

        // First event fails (invalid JSON), second succeeds
        assertThat(event1.getTentativas()).isEqualTo(1);
        assertThat(event1.getPublicadoEm()).isNull();
        assertThat(event2.getPublicadoEm()).isNotNull();
    }

    @Test
    @DisplayName("publishPending with empty routing key")
    void publishPending_emptyRoutingKey() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setRoutingKey("");
        event.setPayload("{}");
        event.setPublicadoEm(null);

        when(repository.lockPendingBatch()).thenReturn(List.of(event));

        relay.publishPending();

        // Should still process
        assertThat(event.getPublicadoEm()).isNotNull();
    }

    @Test
    @DisplayName("T1.4: publishPending sends a parsed JSON object to Rabbit, not the raw string")
    void publishPending_sendsCleanJsonObject_notEncodedString() {
        // eventos.md's envelope is a plain JSON object on the wire. Prior to T1.4 the relay
        // handed the raw JSON *string* column straight to convertAndSend, so
        // Jackson2JsonMessageConverter re-serialized it into a JSON string containing JSON
        // ("{\"...\"}") -- every consumer had to carry a tolerant unwrap to compensate.
        // This test locks in the fix: the object handed to RabbitTemplate must already be a
        // parsed structure (JsonNode), never a String.
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setRoutingKey("wire.format.test");
        event.setPayload("{\"eventId\": \"abc-123\", \"type\": \"test.event\", \"tenantId\": 42}");

        when(repository.lockPendingBatch()).thenReturn(List.of(event));

        relay.publishPending();

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq("wire.format.test"), bodyCaptor.capture());

        Object body = bodyCaptor.getValue();
        assertThat(body).isNotInstanceOf(String.class);
        assertThat(body).isInstanceOf(JsonNode.class);

        JsonNode node = (JsonNode) body;
        assertThat(node.isObject()).isTrue();
        assertThat(node.get("eventId").asText()).isEqualTo("abc-123");
        assertThat(node.get("tenantId").asInt()).isEqualTo(42);
    }

    @Test
    @DisplayName("publishPending idempotent when event already publicado")
    void publishPending_idempotentIfAlreadyPublicado() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setRoutingKey("test");
        event.setPayload("{}");
        event.setPublicadoEm(Instant.now().minusSeconds(100));
        event.setTentativas(0);

        when(repository.lockPendingBatch()).thenReturn(List.of());

        relay.publishPending();

        verify(repository).lockPendingBatch();
    }
}
