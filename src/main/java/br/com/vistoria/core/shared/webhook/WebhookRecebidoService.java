package br.com.vistoria.core.shared.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookRecebidoService {

    private final WebhookRecebidoRepository repository;

    /**
     * Registra o webhook. {@link Optional#empty()} quando o {@code eventId} já foi recebido
     * (reenvio do provedor) — o chamador responde 200 e não republica.
     */
    @Transactional
    public Optional<WebhookRecebido> receber(String origem, String eventId, String payload) {
        if (repository.inserirSeNovo(origem, eventId, payload) == 0) {
            log.info("Webhook duplicado ignorado origem={} eventId={}", origem, eventId);
            return Optional.empty();
        }
        WebhookRecebido w = new WebhookRecebido();
        w.setOrigem(origem);
        w.setEventId(eventId);
        w.setPayload(payload);
        return Optional.of(w);
    }
}
