package br.com.vistoria.core.modules.metrics;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import br.com.vistoria.core.shared.webhook.WebhookRecebidoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recebe webhooks de provedores no inspecta-api, converte via {@link WebhookInterpretador}
 * do broker, deduplica e publica {@code jobs.metrics.status} no outbox. Nao grava
 * metrica — quem persiste e o inspecta-worker.
 */
@Service
@Slf4j
public class MetricasWebhookService {

    private final Map<String, WebhookInterpretador> porBroker;
    private final WebhookRecebidoService webhooksRecebidos;
    private final OutboxService outbox;
    private final ObjectMapper mapper;

    public MetricasWebhookService(List<WebhookInterpretador> interpretadores,
                                  WebhookRecebidoService webhooksRecebidos,
                                  OutboxService outbox,
                                  ObjectMapper mapper) {
        this.porBroker = new HashMap<>();
        for (WebhookInterpretador i : interpretadores) {
            this.porBroker.put(i.broker().toLowerCase(), i);
        }
        this.webhooksRecebidos = webhooksRecebidos;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public void receber(String broker, String payload, Map<String, String> headers) {
        String key = broker == null ? "" : broker.toLowerCase();
        WebhookInterpretador interp = porBroker.get(key);
        if (interp == null) {
            throw new NotFoundException("Nenhum interpretador de webhook para o broker '" + broker + "'");
        }

        JsonNode json;
        try {
            json = mapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Payload de webhook invalido", e);
        }

        MetricaRecebida m = interp.interpretar(json);
        if (m == null || m.externalId() == null || m.externalId().isBlank()) {
            log.info("[metrics] webhook {} sem metrica relevante, ignorado", broker);
            return;
        }

        webhooksRecebidos.receber("metricas-" + key, m.eventId(), payload).ifPresent(w -> {
            Instant ocorridoEm = m.ocorridoEm() != null ? m.ocorridoEm() : Instant.now();
            outbox.publish(EventTypes.METRICS_STATUS, Map.of(
                    "broker", key,
                    "externalId", m.externalId(),
                    "status", m.status().name(),
                    "statusRaw", m.statusRaw() == null ? "" : m.statusRaw(),
                    "ocorridoEm", ocorridoEm.toString()));
        });
    }
}
