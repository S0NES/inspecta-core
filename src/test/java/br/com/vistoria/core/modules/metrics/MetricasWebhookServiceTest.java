package br.com.vistoria.core.modules.metrics;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import br.com.vistoria.core.shared.webhook.WebhookRecebido;
import br.com.vistoria.core.shared.webhook.WebhookRecebidoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricasWebhookService")
class MetricasWebhookServiceTest {

    @Mock private WebhookRecebidoService webhooksRecebidos;
    @Mock private OutboxService outbox;

    private final ObjectMapper mapper = new ObjectMapper();
    private MetricasWebhookService service;

    @BeforeEach
    void setUp() {
        service = new MetricasWebhookService(
                List.of(new ResendWebhookInterpretador()), webhooksRecebidos, outbox, mapper);
    }

    @Test
    @DisplayName("unknown broker -> NotFoundException")
    void unknownBroker() {
        assertThatThrownBy(() -> service.receber("nope", "{}", Map.of()))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("invalid JSON -> IllegalArgumentException")
    void badJson() {
        assertThatThrownBy(() -> service.receber("resend", "not json", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("happy path dedups then publishes jobs.metrics.status")
    @SuppressWarnings("unchecked")
    void happyPath() {
        when(webhooksRecebidos.receber(eq("metricas-resend"), any(), any()))
                .thenReturn(Optional.of(new WebhookRecebido()));

        service.receber("resend",
                "{\"type\":\"email.delivered\",\"data\":{\"email_id\":\"re_1\"}}", Map.of());

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(eq(EventTypes.METRICS_STATUS), body.capture());
        assertThat(body.getValue())
                .containsEntry("broker", "resend")
                .containsEntry("externalId", "re_1")
                .containsEntry("status", "ENTREGUE");
    }

    @Test
    @DisplayName("replay (already seen) does not publish")
    void replay() {
        when(webhooksRecebidos.receber(any(), any(), any())).thenReturn(Optional.empty());

        service.receber("resend",
                "{\"type\":\"email.delivered\",\"data\":{\"email_id\":\"re_1\"}}", Map.of());

        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("irrelevant event (no external id) is ignored, no dedup, no publish")
    void irrelevant() {
        service.receber("resend", "{\"type\":\"email.delivered\",\"data\":{}}", Map.of());

        verifyNoInteractions(webhooksRecebidos);
        verifyNoInteractions(outbox);
    }
}
