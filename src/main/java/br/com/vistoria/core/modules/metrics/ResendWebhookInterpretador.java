package br.com.vistoria.core.modules.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Interpretador de referencia: webhooks de e-mail da Resend.
 *
 * <p>Envelope: {@code { "type": "email.delivered", "created_at": "...",
 * "data": { "email_id": "...", "created_at": "..." } }}. A Resend nao envia um id
 * de evento, entao a chave de dedup e {@code email_id + "|" + type}.</p>
 */
@Component
@Slf4j
public class ResendWebhookInterpretador implements WebhookInterpretador {

    @Override
    public String broker() {
        return "resend";
    }

    @Override
    public MetricaRecebida interpretar(JsonNode payload) {
        if (payload == null) return null;
        String type = text(payload, "type");
        String externalId = text(payload.path("data"), "email_id");
        if (externalId == null || externalId.isBlank()) {
            log.warn("[metrics] webhook resend sem data.email_id, ignorado type={}", type);
            return null;
        }

        StatusEnvio status = mapear(type);
        Instant ocorridoEm = parseInstant(text(payload, "created_at"));
        String eventId = externalId + "|" + (type == null ? "?" : type);
        return new MetricaRecebida(eventId, externalId, status, type, ocorridoEm);
    }

    private static StatusEnvio mapear(String type) {
        if (type == null) return StatusEnvio.DESCONHECIDO;
        return switch (type) {
            case "email.sent", "email.delivery_delayed" -> StatusEnvio.ENVIADO;
            case "email.delivered" -> StatusEnvio.ENTREGUE;
            case "email.opened" -> StatusEnvio.ABERTO;
            case "email.clicked" -> StatusEnvio.CLICADO;
            case "email.bounced" -> StatusEnvio.BOUNCE;
            case "email.complained" -> StatusEnvio.RECLAMACAO;
            case "email.failed" -> StatusEnvio.FALHA;
            default -> StatusEnvio.DESCONHECIDO;
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) return Instant.now();
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }
}
