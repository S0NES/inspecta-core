package br.com.vistoria.core.modules.laudo;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LaudoService")
class LaudoServiceTest {

    @Mock
    private LaudoRepository laudos;

    @Mock
    private OutboxService outbox;

    private LaudoService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new LaudoService(laudos, outbox, mapper);
    }

    @Test
    @DisplayName("criar creates new laudo with default values")
    void criar_createsNewLaudo() {
        Laudo saved = new Laudo();
        saved.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        saved.setSolicitacaoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        saved.setStatus(Laudo.Status.rascunho);
        saved.setVersao(1);

        when(laudos.save(any(Laudo.class))).thenReturn(saved);

        Laudo result = service.criar(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        assertThat(result.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(result.getSolicitacaoServicoId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        assertThat(result.getStatus()).isEqualTo(Laudo.Status.rascunho);
        assertThat(result.getVersao()).isEqualTo(1);
    }

    @Test
    @DisplayName("atualizarConteudo updates laudo JSON content")
    void atualizarConteudo_updatesContent() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        laudo.setStatus(Laudo.Status.rascunho);
        laudo.setConteudoJson("{\"ambientes\":[]}");

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(laudo));

        String newContent = "{\"ambientes\":[{\"uuid\":\"123\"}]}";
        Laudo result = service.atualizarConteudo(UUID.fromString("00000000-0000-0000-0000-000000000001"), newContent);

        assertThat(result.getConteudoJson()).contains("ambientes").contains("uuid");
    }

    @Test
    @DisplayName("atualizarConteudo throws when laudo is assinado")
    void atualizarConteudo_assinado_throws() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        laudo.setStatus(Laudo.Status.assinado);

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(laudo));

        assertThatThrownBy(() -> service.atualizarConteudo(UUID.fromString("00000000-0000-0000-0000-000000000001"), "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("imutavel");
    }

    @Test
    @DisplayName("finalizar sets status to finalizado and publishes PDF_REQUESTED")
    void finalizar_setsStatusAndPublishesEvent() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        laudo.setStatus(Laudo.Status.rascunho);
        laudo.setVersao(1);

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000010"))).thenReturn(Optional.of(laudo));

        service.finalizar(UUID.fromString("00000000-0000-0000-0000-000000000010"));

        assertThat(laudo.getStatus()).isEqualTo(Laudo.Status.finalizado);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(keyCaptor.capture(), eventCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo(EventTypes.PDF_REQUESTED);
        Map event = eventCaptor.getValue();
        assertThat(event).containsKeys("eventId", "laudoId", "versao");
        assertThat(event.get("laudoId")).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000010"));
    }

    @Test
    @DisplayName("finalizar throws when laudo already assinado")
    void finalizar_assinado_throws() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        laudo.setStatus(Laudo.Status.assinado);

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(laudo));

        assertThatThrownBy(() -> service.finalizar(UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("assinado");
    }

    @Test
    @DisplayName("finalizarComPdfExterno finaliza, seta origem e NÃO publica PDF_REQUESTED")
    void finalizarComPdfExterno_naoPublicaPdfRequested() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"));
        laudo.setStatus(Laudo.Status.rascunho);

        when(laudos.findById(laudo.getId())).thenReturn(Optional.of(laudo));

        service.finalizarComPdfExterno(laudo.getId(), "importado_pleno");

        assertThat(laudo.getStatus()).isEqualTo(Laudo.Status.finalizado);
        assertThat(laudo.getOrigem()).isEqualTo(Laudo.Origem.importado_pleno);
        verify(outbox, org.mockito.Mockito.never()).publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("finalizarComPdfExterno em laudo assinado não regride (IllegalStateException)")
    void finalizarComPdfExterno_assinado_naoRegridi() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-0000000000e2"));
        laudo.setStatus(Laudo.Status.assinado);

        when(laudos.findById(laudo.getId())).thenReturn(Optional.of(laudo));

        assertThatThrownBy(() -> service.finalizarComPdfExterno(laudo.getId(), "importado_pleno"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("assinado");
        assertThat(laudo.getStatus()).isEqualTo(Laudo.Status.assinado);
        verify(outbox, org.mockito.Mockito.never()).publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("novaVersao creates new version of assinado laudo")
    void novaVersao_createsNewVersion() {
        Laudo atual = new Laudo();
        atual.setId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        atual.setStatus(Laudo.Status.assinado);
        atual.setVersao(1);
        atual.setSolicitacaoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        atual.setConteudoJson("{\"original\":true}");

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000020"))).thenReturn(Optional.of(atual));

        Laudo nova = new Laudo();
        nova.setId(UUID.fromString("00000000-0000-0000-0000-000000000021"));
        nova.setVersao(2);
        nova.setSolicitacaoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        when(laudos.save(any(Laudo.class))).thenReturn(nova);

        Laudo result = service.novaVersao(UUID.fromString("00000000-0000-0000-0000-000000000020"));

        assertThat(result.getVersao()).isEqualTo(2);
        assertThat(result.getSolicitacaoServicoId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000100"));
    }

    @Test
    @DisplayName("novaVersao throws when laudo not assinado")
    void novaVersao_notAssinado_throws() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        laudo.setStatus(Laudo.Status.rascunho);

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(laudo));

        assertThatThrownBy(() -> service.novaVersao(UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("assinado");
    }

    @Test
    @DisplayName("marcarPdf updates pdfArquivoId when not assinado")
    void marcarPdf_updatesWhenNotAssinado() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000030"));
        laudo.setStatus(Laudo.Status.finalizado);
        laudo.setPdfArquivoId(null);

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000030"))).thenReturn(Optional.of(laudo));

        service.marcarPdf(UUID.fromString("00000000-0000-0000-0000-000000000030"), UUID.fromString("00000000-0000-0000-0000-000000000999"));

        assertThat(laudo.getPdfArquivoId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000999"));
    }

    @Test
    @DisplayName("marcarPdf is idempotent when already assinado (T3.1 replay guard)")
    void marcarPdf_assinado_noOp() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000030"));
        laudo.setStatus(Laudo.Status.assinado);
        laudo.setPdfArquivoId(UUID.fromString("00000000-0000-0000-0000-000000000111"));

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000030"))).thenReturn(Optional.of(laudo));

        // Trying to set a different PDF on already-signed laudo
        service.marcarPdf(UUID.fromString("00000000-0000-0000-0000-000000000030"), UUID.fromString("00000000-0000-0000-0000-000000000222"));

        // Should not update (T3.1 immutability guard)
        assertThat(laudo.getPdfArquivoId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000111"));
    }

    @Test
    @DisplayName("marcarAssinado sets status to assinado and assinadoEm timestamp")
    void marcarAssinado_setsStatusAndTimestamp() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000040"));
        laudo.setStatus(Laudo.Status.finalizado);
        laudo.setAssinadoEm(null);

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000040"))).thenReturn(Optional.of(laudo));

        service.marcarAssinado(UUID.fromString("00000000-0000-0000-0000-000000000040"));

        assertThat(laudo.getStatus()).isEqualTo(Laudo.Status.assinado);
        assertThat(laudo.getAssinadoEm()).isNotNull();
    }

    @Test
    @DisplayName("marcarAssinado is idempotent when already assinado (T3.1 replay guard)")
    void marcarAssinado_assinado_noOp() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000040"));
        laudo.setStatus(Laudo.Status.assinado);
        Instant originalTime = Instant.now().minusSeconds(3600);
        laudo.setAssinadoEm(originalTime);

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000040"))).thenReturn(Optional.of(laudo));

        // Try to mark as signed again (replay)
        service.marcarAssinado(UUID.fromString("00000000-0000-0000-0000-000000000040"));

        // Should not update (idempotent, T3.1 guard)
        assertThat(laudo.getAssinadoEm()).isEqualTo(originalTime);
    }

    @Test
    @DisplayName("get returns laudo when found")
    void get_found() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000050"));

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000050"))).thenReturn(Optional.of(laudo));

        Laudo result = service.get(UUID.fromString("00000000-0000-0000-0000-000000000050"));

        assertThat(result.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000050"));
    }

    @Test
    @DisplayName("get throws NotFoundException when not found")
    void get_notFound() {
        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000999"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(UUID.fromString("00000000-0000-0000-0000-000000000999")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("garantirUuids adds missing UUIDs to ambient items")
    void garantirUuids_addsUuids() throws Exception {
        String json = "{\"ambientes\":[{\"itens\":[{\"nome\":\"item1\"}]}]}";

        // Private method is tested indirectly through atualizarConteudo
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        laudo.setStatus(Laudo.Status.rascunho);

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(laudo));

        Laudo result = service.atualizarConteudo(UUID.fromString("00000000-0000-0000-0000-000000000001"), json);

        // After update, content should have UUIDs
        String content = result.getConteudoJson();
        assertThat(content).contains("uuid");
    }

    @Test
    @DisplayName("garantirUuids skips non-object entries in ambientes/itens without throwing")
    void garantirUuids_skipsNonObjectEntries() throws Exception {
        // "ambientes" mixes a valid object with a stray string entry; the string
        // is not an ObjectNode so it must be skipped instead of blowing up.
        String json = "{\"ambientes\":[\"not-an-object\",{\"itens\":[\"also-not-an-object\",{\"nome\":\"item1\"}]}]}";

        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        laudo.setStatus(Laudo.Status.rascunho);

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(laudo));

        Laudo result = service.atualizarConteudo(UUID.fromString("00000000-0000-0000-0000-000000000001"), json);

        String content = result.getConteudoJson();
        assertThat(content).contains("not-an-object").contains("uuid");
    }

    @Test
    @DisplayName("garantirUuids preserves existing UUIDs instead of overwriting them")
    void garantirUuids_preservesExistingUuid() throws Exception {
        String existingUuid = "existing-uuid-123";
        String json = "{\"ambientes\":[{\"uuid\":\"" + existingUuid + "\",\"itens\":[{\"uuid\":\"item-uuid-456\",\"nome\":\"item1\"}]}]}";

        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        laudo.setStatus(Laudo.Status.rascunho);

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(laudo));

        Laudo result = service.atualizarConteudo(UUID.fromString("00000000-0000-0000-0000-000000000001"), json);

        String content = result.getConteudoJson();
        assertThat(content).contains(existingUuid).contains("item-uuid-456");
    }

    @Test
    @DisplayName("atualizarConteudo with invalid JSON throws IllegalArgumentException")
    void atualizarConteudo_invalidJson_throws() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        laudo.setStatus(Laudo.Status.rascunho);

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(laudo));

        String invalidJson = "not-json";

        assertThatThrownBy(() -> service.atualizarConteudo(UUID.fromString("00000000-0000-0000-0000-000000000001"), invalidJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalido");
    }

    @Test
    @DisplayName("marcarPdf on rascunho status updates without throwing")
    void marcarPdf_rascunho() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000060"));
        laudo.setStatus(Laudo.Status.rascunho);

        when(laudos.findById(UUID.fromString("00000000-0000-0000-0000-000000000060"))).thenReturn(Optional.of(laudo));

        service.marcarPdf(UUID.fromString("00000000-0000-0000-0000-000000000060"), UUID.fromString("00000000-0000-0000-0000-000000000777"));

        assertThat(laudo.getPdfArquivoId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000777"));
    }
}
