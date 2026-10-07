package br.com.vistoria.core.modules.notify;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.shared.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotifyService")
class NotifyServiceTest {

    @Mock
    private NotifyRepositories.MessageTemplateRepository messageTemplates;

    @Mock
    private NotifyRepositories.EntregaRepository entregas;

    @Mock
    private NotifyRepositories.WebhookEventoRepository webhookEventos;

    @Mock
    private NotifyRepositories.TemplateUtilidadeRepository utilidades;

    @Mock
    private OutboxService outbox;

    private NotifyService service;

    @BeforeEach
    void setUp() {
        service = new NotifyService(messageTemplates, entregas, webhookEventos, utilidades, outbox);
    }

    @Test
    @DisplayName("iniciar returns Optional.of(Entrega) on success")
    void iniciar_success_returnsEntrega() {
        UUID id = UUID.randomUUID();
        Entrega expected = new Entrega();
        expected.setId(id);
        expected.setIdempotencyKey("idempotent-123");

        when(entregas.saveAndFlush(any(Entrega.class))).thenReturn(expected);

        Optional<Entrega> result = service.iniciar("idempotent-123", "email", "user@example.com", "template1", UUID.randomUUID());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
        assertThat(result.get().getIdempotencyKey()).isEqualTo("idempotent-123");
    }

    @Test
    @DisplayName("iniciar returns Optional.empty() on duplicate idempotencyKey (collision)")
    void iniciar_duplicate_returnsEmpty() {
        when(entregas.saveAndFlush(any(Entrega.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        Optional<Entrega> result = service.iniciar("duplicate-key", "sms", "+5511999999999", "template2", UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("iniciar sets all fields correctly")
    void iniciar_setsAllFields() {
        UUID id = UUID.randomUUID();
        when(entregas.saveAndFlush(any(Entrega.class))).thenAnswer(invocation -> {
            Entrega e = invocation.getArgument(0);
            e.setId(id);
            return e;
        });

        String key = "test-key-456";
        String canal = "whatsapp";
        String destinatario = "+5521987654321";
        String templateId = "welcome";
        UUID tenantId = UUID.randomUUID();

        Optional<Entrega> result = service.iniciar(key, canal, destinatario, templateId, tenantId);

        assertThat(result).isPresent();
        Entrega entrega = result.get();
        assertThat(entrega.getIdempotencyKey()).isEqualTo(key);
        assertThat(entrega.getCanal()).isEqualTo(canal);
        assertThat(entrega.getDestinatario()).isEqualTo(destinatario);
        assertThat(entrega.getTemplateId()).isEqualTo(templateId);
        assertThat(entrega.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("template resolves a UUID templateId against message_templates (latest active version)")
    void template_uuid_resolvesFromMessageTemplates() {
        UUID uuid = UUID.fromString("00000000-0000-4000-8000-000000000001");
        MessageTemplate mt = new MessageTemplate();
        mt.setUuid(uuid);
        mt.setVersao(3);
        mt.setCanal("email");
        mt.setAssunto("Assunto");
        mt.setCorpo("Ola {{nome}}");
        when(messageTemplates.findFirstByUuidAndAtivoTrueOrderByVersaoDesc(uuid))
                .thenReturn(Optional.of(mt));

        Optional<MessageTemplate> result = service.template(uuid.toString(), "email");

        assertThat(result).isPresent();
        assertThat(result.get().getCorpo()).isEqualTo("Ola {{nome}}");
        assertThat(result.get().getUuid()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("template returns Optional.empty when no active version exists for the UUID")
    void template_notFound() {
        UUID uuid = UUID.randomUUID();
        when(messageTemplates.findFirstByUuidAndAtivoTrueOrderByVersaoDesc(uuid))
                .thenReturn(Optional.empty());

        Optional<MessageTemplate> result = service.template(uuid.toString(), "email");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("template throws for a non-UUID templateId (legacy notify.templates fallback removed)")
    void template_nonUuid_throws() {
        assertThatThrownBy(() -> service.template("welcome", "email"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("porIdempotencyKey returns Entrega when found")
    void porIdempotencyKey_found() {
        Entrega entrega = new Entrega();
        entrega.setIdempotencyKey("key-123");

        when(entregas.findByIdempotencyKey("key-123")).thenReturn(Optional.of(entrega));

        Optional<Entrega> result = service.porIdempotencyKey("key-123");

        assertThat(result).isPresent();
        assertThat(result.get().getIdempotencyKey()).isEqualTo("key-123");
    }

    @Test
    @DisplayName("concluir updates status to enviado")
    void concluir_updatesStatus() {
        Entrega entrega = new Entrega();
        entrega.setIdempotencyKey("key-to-conclude");
        entrega.setStatus(Entrega.Status.enviando.name());

        when(entregas.findByIdempotencyKey("key-to-conclude")).thenReturn(Optional.of(entrega));

        service.concluir("key-to-conclude", "external-123", 1);

        assertThat(entrega.getStatus()).isEqualTo(Entrega.Status.enviado.name());
        assertThat(entrega.getExternalId()).isEqualTo("external-123");
        assertThat(entrega.getTentativas()).isEqualTo(1);
        assertThat(entrega.getAtualizadoEm()).isNotNull();
    }

    @Test
    @DisplayName("concluir publishes jobs.metrics.enviado with eventId == idempotencyKey")
    @SuppressWarnings("unchecked")
    void concluir_publishesMetricsEnviado() {
        Entrega entrega = new Entrega();
        entrega.setIdempotencyKey("reset-7-42");
        entrega.setCanal("email");
        entrega.setStatus(Entrega.Status.enviando.name());
        when(entregas.findByIdempotencyKey("reset-7-42")).thenReturn(Optional.of(entrega));

        service.concluir("reset-7-42", "resend-abc", 1);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(eq(EventTypes.METRICS_ENVIADO), body.capture());
        assertThat(body.getValue())
                .containsEntry("eventId", "reset-7-42")
                .containsEntry("externalId", "resend-abc")
                .containsEntry("canal", "email");
    }

    @Test
    @DisplayName("concluir does nothing when the entrega is absent")
    void concluir_absent_noop() {
        when(entregas.findByIdempotencyKey("missing")).thenReturn(Optional.empty());

        service.concluir("missing", "ext", 1);

        verify(outbox, org.mockito.Mockito.never()).publish(any(), any());
    }

    @Test
    @DisplayName("falhar updates status to falhou")
    void falhar_updatesStatus() {
        Entrega entrega = new Entrega();
        entrega.setIdempotencyKey("key-to-fail");
        entrega.setStatus(Entrega.Status.enviando.name());

        when(entregas.findByIdempotencyKey("key-to-fail")).thenReturn(Optional.of(entrega));

        service.falhar("key-to-fail", 3, "Network timeout");

        assertThat(entrega.getStatus()).isEqualTo(Entrega.Status.falhou.name());
        assertThat(entrega.getTentativas()).isEqualTo(3);
        assertThat(entrega.getErro()).isEqualTo("Network timeout");
        assertThat(entrega.getAtualizadoEm()).isNotNull();
    }

    @Test
    @DisplayName("registrarWebhook returns true on success")
    void registrarWebhook_success() {
        when(webhookEventos.saveAndFlush(any(NotifyWebhookEvento.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = service.registrarWebhook("resend", "event-123");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("registrarWebhook returns false on duplicate")
    void registrarWebhook_duplicate() {
        when(webhookEventos.saveAndFlush(any(NotifyWebhookEvento.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate"));

        boolean result = service.registrarWebhook("resend", "event-456");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("atualizarStatusPorExternalId updates status when found")
    void atualizarStatusPorExternalId_found() {
        Entrega entrega = new Entrega();
        entrega.setExternalId("ext-789");
        entrega.setStatus(Entrega.Status.enviando.name());

        when(entregas.findFirstByExternalIdOrderByIdDesc("ext-789"))
                .thenReturn(Optional.of(entrega));

        service.atualizarStatusPorExternalId("ext-789", Entrega.Status.enviado.name());

        assertThat(entrega.getStatus()).isEqualTo(Entrega.Status.enviado.name());
        assertThat(entrega.getAtualizadoEm()).isNotNull();
    }

    @Test
    @DisplayName("collision handling: second iniciar with same key returns empty")
    void collision_idempotency() {
        UUID tenantId = UUID.randomUUID();
        when(entregas.saveAndFlush(any(Entrega.class)))
                .thenReturn(new Entrega())
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        Optional<Entrega> first = service.iniciar("same-key", "email", "test@example.com", "t1", tenantId);
        Optional<Entrega> second = service.iniciar("same-key", "sms", "+55123", "t2", tenantId);

        assertThat(first).isPresent();
        assertThat(second).isEmpty(); // Collision returns empty, not error
    }
}
