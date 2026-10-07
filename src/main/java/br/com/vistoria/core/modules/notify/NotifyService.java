package br.com.vistoria.core.modules.notify;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.modules.notify.NotifyRepositories.*;
import br.com.vistoria.core.shared.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotifyService {

    private final MessageTemplateRepository messageTemplates;
    private final EntregaRepository entregas;
    private final WebhookEventoRepository webhookEventos;
    private final TemplateUtilidadeRepository utilidades;
    private final OutboxService outbox;

    /**
     * Resolve o template de uma entrega: {@code templateId} e sempre um UUID -> maior versao
     * ativa em {@code notify.message_templates}. A tabela legada {@code notify.templates} e o
     * fallback por slug foram removidos junto com a migracao UUID (nao ha mais mensagem em
     * transito publicada antes dela).
     */
    @Transactional(readOnly = true)
    public Optional<MessageTemplate> template(String templateId, String canal) {
        UUID uuid = UUID.fromString(templateId);
        // Se este UUID e o padrao de uma utilidade (reset, convite...), o template efetivo
        // e o que estiver configurado em notify.template_utilidades (ADMIN troca pela tela).
        UUID efetivo = TemplateUtilidade.porUuidPadrao(uuid.toString())
                .flatMap(u -> utilidades.findById(u.name()))
                .map(TemplateUtilidadeEntity::getTemplateId)
                .orElse(uuid);
        return messageTemplates.findFirstByUuidAndAtivoTrueOrderByVersaoDesc(efetivo);
    }

    @Transactional(readOnly = true)
    public Optional<Entrega> porIdempotencyKey(String key) {
        return entregas.findByIdempotencyKey(key);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Entrega> iniciar(String idempotencyKey, String canal, String destinatario, String templateId, UUID tenantId) {
        Entrega e = new Entrega();
        e.setIdempotencyKey(idempotencyKey);
        e.setCanal(canal);
        e.setDestinatario(destinatario);
        e.setTemplateId(templateId);
        e.setTenantId(tenantId);
        try {
            return Optional.of(entregas.saveAndFlush(e));
        } catch (DataIntegrityViolationException dup) {
            log.info("[notify] iniciar duplicado ignorado idempotencyKey={}", idempotencyKey);
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void concluir(String idempotencyKey, String externalId, int tentativas) {
        entregas.findByIdempotencyKey(idempotencyKey).ifPresent(e -> {
            e.setStatus(Entrega.Status.enviado.name());
            e.setExternalId(externalId);
            e.setTentativas(tentativas);
            e.setAtualizadoEm(Instant.now());
            // Metricas de envio: o inspecta-messenger acabou de enviar de fato. O inspecta-worker consome
            // este evento e preenche metrics.envios.data_envio / external_id.
            outbox.publish(EventTypes.METRICS_ENVIADO, Map.of(
                    "eventId", idempotencyKey,
                    "externalId", externalId == null ? "" : externalId,
                    "canal", e.getCanal() == null ? "" : e.getCanal(),
                    "ocorridoEm", Instant.now().toString()));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void falhar(String idempotencyKey, int tentativas, String erro) {
        entregas.findByIdempotencyKey(idempotencyKey).ifPresent(e -> {
            e.setStatus(Entrega.Status.falhou.name());
            e.setTentativas(tentativas);
            e.setErro(erro);
            e.setAtualizadoEm(Instant.now());
        });
    }

    @Transactional
    public boolean registrarWebhook(String broker, String eventId) {
        try {
            NotifyWebhookEvento w = new NotifyWebhookEvento();
            w.setBroker(broker);
            w.setEventId(eventId);
            webhookEventos.saveAndFlush(w);
            return true;
        } catch (DataIntegrityViolationException dup) {
            log.info("Webhook broker duplicado ignorado broker={} eventId={}", broker, eventId);
            return false;
        }
    }

    @Transactional
    public void atualizarStatusPorExternalId(String externalId, String status) {
        entregas.findFirstByExternalIdOrderByIdDesc(externalId).ifPresent(e -> {
            e.setStatus(status);
            e.setAtualizadoEm(Instant.now());
        });
    }
}
