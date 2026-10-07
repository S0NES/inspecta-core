package br.com.vistoria.core.modules.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResendWebhookInterpretador")
class ResendWebhookInterpretadorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ResendWebhookInterpretador interp = new ResendWebhookInterpretador();

    private MetricaRecebida interpretar(String json) throws Exception {
        return interp.interpretar(mapper.readTree(json));
    }

    @Test
    @DisplayName("broker is 'resend'")
    void broker() {
        assertThat(interp.broker()).isEqualTo("resend");
    }

    @Test
    @DisplayName("maps email.delivered to ENTREGUE with externalId and timestamp")
    void delivered() throws Exception {
        MetricaRecebida m = interpretar("""
            { "type": "email.delivered", "created_at": "2026-08-01T12:00:00Z",
              "data": { "email_id": "re_abc123" } }
            """);

        assertThat(m.status()).isEqualTo(StatusEnvio.ENTREGUE);
        assertThat(m.externalId()).isEqualTo("re_abc123");
        assertThat(m.statusRaw()).isEqualTo("email.delivered");
        assertThat(m.eventId()).isEqualTo("re_abc123|email.delivered");
        assertThat(m.ocorridoEm()).isEqualTo(Instant.parse("2026-08-01T12:00:00Z"));
    }

    @Test
    @DisplayName("maps the rest of the Resend event vocabulary")
    void vocab() throws Exception {
        assertThat(interpretar("{\"type\":\"email.sent\",\"data\":{\"email_id\":\"x\"}}").status())
                .isEqualTo(StatusEnvio.ENVIADO);
        assertThat(interpretar("{\"type\":\"email.bounced\",\"data\":{\"email_id\":\"x\"}}").status())
                .isEqualTo(StatusEnvio.BOUNCE);
        assertThat(interpretar("{\"type\":\"email.complained\",\"data\":{\"email_id\":\"x\"}}").status())
                .isEqualTo(StatusEnvio.RECLAMACAO);
        assertThat(interpretar("{\"type\":\"email.opened\",\"data\":{\"email_id\":\"x\"}}").status())
                .isEqualTo(StatusEnvio.ABERTO);
        assertThat(interpretar("{\"type\":\"email.clicked\",\"data\":{\"email_id\":\"x\"}}").status())
                .isEqualTo(StatusEnvio.CLICADO);
        assertThat(interpretar("{\"type\":\"email.failed\",\"data\":{\"email_id\":\"x\"}}").status())
                .isEqualTo(StatusEnvio.FALHA);
        assertThat(interpretar("{\"type\":\"something.new\",\"data\":{\"email_id\":\"x\"}}").status())
                .isEqualTo(StatusEnvio.DESCONHECIDO);
    }

    @Test
    @DisplayName("returns null when data.email_id is missing")
    void missingId() throws Exception {
        assertThat(interpretar("{\"type\":\"email.delivered\",\"data\":{}}")).isNull();
    }

    @Test
    @DisplayName("null payload returns null")
    void nullPayload() {
        assertThat(interp.interpretar(null)).isNull();
    }

    @Test
    @DisplayName("falls back to now() when created_at is missing or invalid")
    void badTimestamp() throws Exception {
        MetricaRecebida m = interpretar("{\"type\":\"email.sent\",\"created_at\":\"not-a-date\",\"data\":{\"email_id\":\"x\"}}");
        assertThat(m.ocorridoEm()).isBetween(Instant.now().minusSeconds(5), Instant.now().plusSeconds(5));
    }
}
