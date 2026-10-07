package br.com.vistoria.core.modules.assinatura;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.modules.laudo.Laudo;
import br.com.vistoria.core.modules.laudo.LaudoService;
import br.com.vistoria.core.modules.notify.NotifyTemplates;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoInterveniente;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoService;
import br.com.vistoria.core.shared.outbox.OutboxService;
import br.com.vistoria.core.shared.webhook.WebhookRecebidoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssinaturaService")
class AssinaturaServiceTest {

    @Mock
    private AssinaturaService.SignatarioRepository signatarios;

    @Mock
    private WebhookRecebidoService webhooks;

    @Mock
    private LaudoService laudoService;

    @Mock
    private SolicitacaoService solicitacaoService;

    @Mock
    private OutboxService outbox;

    private AssinaturaService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new AssinaturaService(signatarios, webhooks, laudoService, solicitacaoService, outbox, mapper);
    }

    @Test
    @DisplayName("documentoJaConcluido returns true when laudo is assinado")
    void documentoJaConcluido_assinado_returnsTrue() {
        Signatario sig = new Signatario();
        sig.setDocumentoTipo("laudo");
        sig.setDocumentoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        when(signatarios.findByExternalDocumentId("doc-1")).thenReturn(List.of(sig));

        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        laudo.setStatus(Laudo.Status.assinado);
        when(laudoService.get(UUID.fromString("00000000-0000-0000-0000-000000000100"))).thenReturn(laudo);

        assertThat(service.documentoJaConcluido("doc-1")).isTrue();
    }

    @Test
    @DisplayName("documentoJaConcluido returns false when laudo not assinado")
    void documentoJaConcluido_notAssinado_returnsFalse() {
        Signatario sig = new Signatario();
        sig.setDocumentoTipo("laudo");
        sig.setDocumentoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        when(signatarios.findByExternalDocumentId("doc-2")).thenReturn(List.of(sig));

        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        laudo.setStatus(Laudo.Status.finalizado);
        when(laudoService.get(UUID.fromString("00000000-0000-0000-0000-000000000100"))).thenReturn(laudo);

        assertThat(service.documentoJaConcluido("doc-2")).isFalse();
    }

    @Test
    @DisplayName("documentoJaConcluido returns false when no signatarios found")
    void documentoJaConcluido_noSignatarios_returnsFalse() {
        when(signatarios.findByExternalDocumentId("nonexistent")).thenReturn(Collections.emptyList());
        assertThat(service.documentoJaConcluido("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("documentoJaConcluido returns false when documento type is not laudo")
    void documentoJaConcluido_notLaudo_returnsFalse() {
        Signatario sig = new Signatario();
        sig.setDocumentoTipo("contrato");
        sig.setDocumentoId(UUID.fromString("00000000-0000-0000-0000-000000000200"));

        when(signatarios.findByExternalDocumentId("doc-3")).thenReturn(List.of(sig));

        assertThat(service.documentoJaConcluido("doc-3")).isFalse();
    }

    @Test
    @DisplayName("aplicarDocumentoAssinado marks signatarios as assinado and updates laudo")
    void aplicarDocumentoAssinado_success() {
        Signatario sig1 = new Signatario();
        sig1.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        sig1.setDocumentoTipo("laudo");
        sig1.setDocumentoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        sig1.setStatusAssinatura("pendente");
        sig1.setEmail("user1@example.com");
        sig1.setNome("User One");

        Signatario sig2 = new Signatario();
        sig2.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        sig2.setDocumentoTipo("laudo");
        sig2.setDocumentoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        sig2.setStatusAssinatura("pendente");
        sig2.setEmail("user2@example.com");
        sig2.setNome("User Two");

        when(signatarios.findByExternalDocumentId("doc-complete")).thenReturn(List.of(sig1, sig2));

        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        laudo.setStatus(Laudo.Status.finalizado);
        when(laudoService.get(UUID.fromString("00000000-0000-0000-0000-000000000100"))).thenReturn(laudo);

        service.aplicarDocumentoAssinado("doc-complete", UUID.fromString("00000000-0000-0000-0000-000000000500"));

        assertThat(sig1.getStatusAssinatura()).isEqualTo("assinado");
        assertThat(sig2.getStatusAssinatura()).isEqualTo("assinado");
        verify(laudoService).marcarPdf(UUID.fromString("00000000-0000-0000-0000-000000000100"), UUID.fromString("00000000-0000-0000-0000-000000000500"));
        verify(laudoService).marcarAssinado(UUID.fromString("00000000-0000-0000-0000-000000000100"));
    }

    @Test
    @DisplayName("aplicarDocumentoAssinado ignores replay when laudo already assinado (T3.1)")
    void aplicarDocumentoAssinado_replay_noOp() {
        Signatario sig = new Signatario();
        sig.setDocumentoTipo("laudo");
        sig.setDocumentoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        sig.setStatusAssinatura("assinado");

        when(signatarios.findByExternalDocumentId("already-signed")).thenReturn(List.of(sig));

        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        laudo.setStatus(Laudo.Status.assinado);
        when(laudoService.get(UUID.fromString("00000000-0000-0000-0000-000000000100"))).thenReturn(laudo);

        service.aplicarDocumentoAssinado("already-signed", UUID.fromString("00000000-0000-0000-0000-000000000600"));

        verify(laudoService, never()).marcarPdf(any(UUID.class), any(UUID.class));
        verify(laudoService, never()).marcarAssinado(any(UUID.class));
    }

    @Test
    @DisplayName("aplicarDocumentoAssinado with no signatarios logs warning")
    void aplicarDocumentoAssinado_noSignatarios() {
        when(signatarios.findByExternalDocumentId("no-signatarios")).thenReturn(Collections.emptyList());
        service.aplicarDocumentoAssinado("no-signatarios", UUID.fromString("00000000-0000-0000-0000-000000000700"));
        verify(laudoService, never()).marcarPdf(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("aplicarDocumentoAssinado publishes notify event")
    void aplicarDocumentoAssinado_publishes_notifyEvent() {
        Signatario sig = new Signatario();
        sig.setId(UUID.fromString("00000000-0000-0000-0000-000000000050"));
        sig.setDocumentoTipo("laudo");
        sig.setDocumentoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        sig.setStatusAssinatura("pendente");
        sig.setEmail("user@example.com");
        sig.setNome("João Silva");

        when(signatarios.findByExternalDocumentId("doc-notify")).thenReturn(List.of(sig));

        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        laudo.setStatus(Laudo.Status.finalizado);
        when(laudoService.get(UUID.fromString("00000000-0000-0000-0000-000000000100"))).thenReturn(laudo);

        service.aplicarDocumentoAssinado("doc-notify", UUID.fromString("00000000-0000-0000-0000-000000000800"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outbox).publish(keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).contains("notify.requested");
    }

    @Test
    @DisplayName("solicitarAssinatura monta signatários de solicitacao_intervenientes e publica SIGNATURE_REQUESTED")
    void solicitarAssinatura_publishes_event() {
        UUID laudoId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        UUID servicoId = UUID.fromString("00000000-0000-0000-0000-000000000200");

        Laudo laudo = new Laudo();
        laudo.setSolicitacaoServicoId(servicoId);
        when(laudoService.get(laudoId)).thenReturn(laudo);

        SolicitacaoInterveniente p = new SolicitacaoInterveniente();
        p.setNome("Ana Silva");
        p.setEmail("ana@x.co");
        p.setPapelSlug("locador");
        when(solicitacaoService.intervenientesDoServico(servicoId)).thenReturn(List.of(p));

        service.solicitarAssinatura("laudo", laudoId, List.of(new Signatario()));

        ArgumentCaptor<List<Signatario>> saved = ArgumentCaptor.forClass(List.class);
        verify(signatarios).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(s -> {
            assertThat(s.getNome()).isEqualTo("Ana Silva");
            assertThat(s.getEmail()).isEqualTo("ana@x.co");
            assertThat(s.getPapel()).isEqualTo("locador");
            assertThat(s.getDocumentoId()).isEqualTo(laudoId);
        });

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outbox).publish(keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).isEqualTo(EventTypes.SIGNATURE_REQUESTED);
    }

    @Test
    @DisplayName("solicitarAssinatura sem interveniente na solicitação → 'sem_intervenientes'")
    void solicitarAssinatura_semIntervenientes() {
        UUID laudoId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        Laudo laudo = new Laudo();
        laudo.setSolicitacaoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000201"));
        when(laudoService.get(laudoId)).thenReturn(laudo);
        when(solicitacaoService.intervenientesDoServico(any())).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.solicitarAssinatura("laudo", laudoId, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("sem_intervenientes");
        verify(signatarios, never()).saveAll(any());
    }

    @Test
    @DisplayName("vincularDocumento grava ids/signing_url e publica e-mail do link")
    void vincularDocumento_linksAndNotifies() {
        Signatario sig = new Signatario();
        sig.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        sig.setNome("Ana Silva");
        sig.setEmail("ana@x.co");

        when(signatarios.findById(UUID.fromString("00000000-0000-0000-0000-000000000010"))).thenReturn(Optional.of(sig));

        service.vincularDocumento(UUID.fromString("00000000-0000-0000-0000-000000000010"),
                "signer-123", "doc-456", "https://assina.autentique.com.br/abc");

        assertThat(sig.getExternalSignerId()).isEqualTo("signer-123");
        assertThat(sig.getExternalDocumentId()).isEqualTo("doc-456");
        assertThat(sig.getSigningUrl()).isEqualTo("https://assina.autentique.com.br/abc");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(keyCaptor.capture(), bodyCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo("notify.requested.assinatura-link");
        assertThat(bodyCaptor.getValue()).containsEntry("templateId", NotifyTemplates.ASSINATURA_LINK);
        assertThat(bodyCaptor.getValue()).containsEntry("destinatario", "ana@x.co");
        @SuppressWarnings("unchecked")
        Map<String, Object> variaveis = (Map<String, Object>) bodyCaptor.getValue().get("variaveis");
        assertThat(variaveis).containsEntry("signingUrl", "https://assina.autentique.com.br/abc");
    }

    @Test
    @DisplayName("vincularDocumento sem signing_url não publica e-mail")
    void vincularDocumento_semSigningUrl_noEmail() {
        Signatario sig = new Signatario();
        sig.setId(UUID.fromString("00000000-0000-0000-0000-000000000011"));
        when(signatarios.findById(UUID.fromString("00000000-0000-0000-0000-000000000011"))).thenReturn(Optional.of(sig));

        service.vincularDocumento(UUID.fromString("00000000-0000-0000-0000-000000000011"), "signer-x", "doc-x", null);

        verify(outbox, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("aplicarStatus updates status assinatura por externalSignerId")
    void aplicarStatus_updatesStatus() {
        Signatario sig = new Signatario();
        sig.setId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        sig.setStatusAssinatura("pendente");
        when(signatarios.findByExternalSignerId("signer-1")).thenReturn(Optional.of(sig));

        service.aplicarStatus("signer-1", "assinado");

        assertThat(sig.getStatusAssinatura()).isEqualTo("assinado");
    }

    @Test
    @DisplayName("aplicarEventoAssinatura signature.rejected marca signatario como recusado")
    void aplicarEventoAssinatura_rejected() {
        Signatario sig = new Signatario();
        sig.setStatusAssinatura("pendente");
        when(signatarios.findByExternalSignerId("signer-r")).thenReturn(Optional.of(sig));

        service.aplicarEventoAssinatura("evt-1", "signature.rejected", "doc-1", "signer-r");

        assertThat(sig.getStatusAssinatura()).isEqualTo("recusado");
    }

    @Test
    @DisplayName("aplicarEventoAssinatura evento sem ação de domínio é no-op")
    void aplicarEventoAssinatura_noop() {
        service.aplicarEventoAssinatura("evt-2", "signature.viewed", "doc-1", "signer-v");
        verifyNoInteractions(signatarios);
    }

    @Test
    @DisplayName("aplicarStatus 'assinado' carimba assinado_em")
    void aplicarStatus_carimbaAssinadoEm() {
        Signatario sig = new Signatario();
        sig.setDocumentoTipo("laudo");
        sig.setDocumentoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        sig.setStatusAssinatura("pendente");
        Signatario outro = new Signatario();  // ainda pendente -> não conclui o documento
        outro.setStatusAssinatura("pendente");
        when(signatarios.findByExternalSignerId("signer-a")).thenReturn(Optional.of(sig));
        when(signatarios.findByDocumentoTipoAndDocumentoId("laudo", sig.getDocumentoId())).thenReturn(List.of(sig, outro));

        service.aplicarStatus("signer-a", "assinado");

        assertThat(sig.getAssinadoEm()).isNotNull();
    }

    @Test
    @DisplayName("pendentesParaLembrete passa janela e intervalo como instantes relativos a agora")
    void pendentesParaLembrete_traduzDuracoes() {
        when(signatarios.pendentesParaLembrete(any(), any())).thenReturn(List.of());

        service.pendentesParaLembrete(java.time.Duration.ofDays(7), java.time.Duration.ofHours(72));

        ArgumentCaptor<java.time.Instant> janela = ArgumentCaptor.forClass(java.time.Instant.class);
        ArgumentCaptor<java.time.Instant> intervalo = ArgumentCaptor.forClass(java.time.Instant.class);
        verify(signatarios).pendentesParaLembrete(janela.capture(), intervalo.capture());
        java.time.Instant agora = java.time.Instant.now();
        assertThat(janela.getValue()).isBefore(agora.minus(java.time.Duration.ofDays(6)));
        assertThat(intervalo.getValue()).isBetween(agora.minus(java.time.Duration.ofHours(73)), agora.minus(java.time.Duration.ofHours(71)));
    }

    @Test
    @DisplayName("registrarLembreteEnviado carimba lembrete_enviado_em")
    void registrarLembreteEnviado_carimba() {
        Signatario sig = new Signatario();
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        when(signatarios.findById(id)).thenReturn(Optional.of(sig));

        service.registrarLembreteEnviado(id);

        assertThat(sig.getLembreteEnviadoEm()).isNotNull();
    }

    @Test
    @DisplayName("receberWebhook dedup + publica SIGNATURE_WEBHOOK com o payload")
    void receberWebhook_publishesEvent() {
        String payload = "{\"event\":{\"id\":\"evt-123\",\"type\":\"signature.accepted\",\"data\":{\"object\":{\"document\":\"doc-abc\"}}}}";

        br.com.vistoria.core.shared.webhook.WebhookRecebido webhook = new br.com.vistoria.core.shared.webhook.WebhookRecebido();
        webhook.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        webhook.setOrigem("autentique");
        webhook.setEventId("evt-123");
        webhook.setPayload(payload);
        when(webhooks.receber("autentique", "evt-123", payload)).thenReturn(Optional.of(webhook));

        service.receberWebhook(payload, Map.of());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(keyCaptor.capture(), bodyCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo(EventTypes.SIGNATURE_WEBHOOK);
        assertThat(bodyCaptor.getValue()).containsEntry("eventId", "evt-123");
        assertThat(bodyCaptor.getValue()).containsEntry("payload", payload);
    }

    @Test
    @DisplayName("receberWebhook com JSON inválido não lança")
    void receberWebhook_invalidJson() {
        service.receberWebhook("not-json", Map.of());
        verify(outbox, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("receberWebhook sem event.id não publica")
    void receberWebhook_missingEventId() {
        service.receberWebhook("{\"event\":{\"type\":\"signature.accepted\"}}", Map.of());
        verify(outbox, never()).publish(anyString(), any());
        verifyNoInteractions(webhooks);
    }

    @Test
    @DisplayName("receberWebhook ignora evento sem efeito de domínio (signature.viewed / signature.updated)")
    void receberWebhook_eventoIrrelevante() {
        service.receberWebhook("{\"event\":{\"id\":\"evt-v\",\"type\":\"signature.viewed\"}}", Map.of());
        service.receberWebhook("{\"event\":{\"id\":\"evt-u\",\"type\":\"signature.updated\"}}", Map.of());
        verifyNoInteractions(webhooks);
        verify(outbox, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("receberWebhook duplicado (dedup) não publica")
    void receberWebhook_duplicate() {
        String payload = "{\"event\":{\"id\":\"evt-dup\",\"type\":\"signature.accepted\"}}";
        when(webhooks.receber("autentique", "evt-dup", payload)).thenReturn(Optional.empty());

        service.receberWebhook(payload, Map.of());

        verify(outbox, never()).publish(anyString(), any());
    }
}
