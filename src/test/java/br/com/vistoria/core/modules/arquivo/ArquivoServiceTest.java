package br.com.vistoria.core.modules.arquivo;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArquivoService")
class ArquivoServiceTest {

    @Mock
    private ArquivoRepository arquivos;

    @Mock
    private OutboxService outbox;

    private ArquivoService service;

    @BeforeEach
    void setUp() {
        service = new ArquivoService(arquivos, outbox);
    }

    @Test
    @DisplayName("registrar creates and saves arquivo")
    void registrar_createsArquivo() {
        Arquivo saved = new Arquivo();
        saved.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        saved.setR2Key("s3://bucket/file.pdf");
        saved.setMime("application/pdf");
        saved.setTamanho(1024L);
        saved.setOwnerType("laudo");
        saved.setOwnerId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        saved.setProcessado(false);

        when(arquivos.findByR2Key("s3://bucket/file.pdf")).thenReturn(Optional.empty());
        when(arquivos.save(any(Arquivo.class))).thenReturn(saved);

        Arquivo result = service.registrar("s3://bucket/file.pdf", "application/pdf", 1024L, "laudo", UUID.fromString("00000000-0000-0000-0000-000000000100"), false);

        assertThat(result.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(result.getR2Key()).isEqualTo("s3://bucket/file.pdf");
        assertThat(result.getMime()).isEqualTo("application/pdf");
        assertThat(result.getOwnerType()).isEqualTo("laudo");
        assertThat(result.getOwnerId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        assertThat(result.isProcessado()).isFalse();
    }

    @Test
    @DisplayName("registrar 2x a mesma r2_key devolve o existente e nao grava outro")
    void registrar_mesmaR2Key_idempotente() {
        Arquivo existente = new Arquivo();
        existente.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        existente.setR2Key("laudos/l1/pleno-v1.pdf");
        when(arquivos.findByR2Key("laudos/l1/pleno-v1.pdf")).thenReturn(Optional.of(existente));

        Arquivo result = service.registrar("laudos/l1/pleno-v1.pdf", "application/pdf", 10L, "laudo",
                UUID.fromString("00000000-0000-0000-0000-000000000100"), true);

        assertThat(result).isSameAs(existente);
        verify(arquivos, never()).save(any(Arquivo.class));
    }

    @Test
    @DisplayName("registrar em corrida: unique em r2_key -> relê a linha ganhadora")
    void registrar_corridaUnique_rele() {
        Arquivo ganhadora = new Arquivo();
        ganhadora.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        ganhadora.setR2Key("laudos/l1/pleno-v1.pdf");
        when(arquivos.findByR2Key("laudos/l1/pleno-v1.pdf"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ganhadora));
        when(arquivos.save(any(Arquivo.class))).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("uk_r2_key"));

        Arquivo result = service.registrar("laudos/l1/pleno-v1.pdf", "application/pdf", 10L, "laudo",
                UUID.fromString("00000000-0000-0000-0000-000000000100"), true);

        assertThat(result).isSameAs(ganhadora);
    }

    @Test
    @DisplayName("get returns arquivo when exists")
    void get_returnsArquivo() {
        Arquivo arquivo = new Arquivo();
        arquivo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        arquivo.setR2Key("test.pdf");

        when(arquivos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(arquivo));

        Arquivo result = service.get(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThat(result).isEqualTo(arquivo);
    }

    @Test
    @DisplayName("get throws NotFoundException when arquivo not found")
    void get_throwsNotFound() {
        when(arquivos.findById(UUID.fromString("00000000-0000-0000-0000-000000000999"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(UUID.fromString("00000000-0000-0000-0000-000000000999")))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("999");
    }

    @Test
    @DisplayName("confirmarUpload publishes image event for image mime types")
    void confirmarUpload_publishesImageEvent() {
        Arquivo arquivo = new Arquivo();
        arquivo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        arquivo.setMime("image/png");
        arquivo.setR2Key("s3://bucket/image.png");

        when(arquivos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(arquivo));

        service.confirmarUpload(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(eq(EventTypes.IMAGE_UPLOADED), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload).containsKeys("eventId", "arquivoId", "r2Key");
        assertThat(payload.get("arquivoId")).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    @DisplayName("confirmarUpload marks non-image arquivo as processado")
    void confirmarUpload_markProcessadoForNonImage() {
        Arquivo arquivo = new Arquivo();
        arquivo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        arquivo.setMime("application/pdf");
        arquivo.setProcessado(false);

        when(arquivos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(arquivo));

        service.confirmarUpload(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThat(arquivo.isProcessado()).isTrue();
        verify(outbox, never()).publish(any(), any());
    }

    @Test
    @DisplayName("marcarProcessado updates arquivo when found")
    void marcarProcessado_updatesArquivo() {
        Arquivo arquivo = new Arquivo();
        arquivo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        arquivo.setProcessado(false);

        when(arquivos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(arquivo));

        service.marcarProcessado(UUID.fromString("00000000-0000-0000-0000-000000000001"), "thumb.png");

        assertThat(arquivo.isProcessado()).isTrue();
        assertThat(arquivo.getThumbKey()).isEqualTo("thumb.png");
    }

    @Test
    @DisplayName("marcarProcessado does nothing if arquivo not found")
    void marcarProcessado_notFound() {
        when(arquivos.findById(UUID.fromString("00000000-0000-0000-0000-000000000999"))).thenReturn(Optional.empty());

        service.marcarProcessado(UUID.fromString("00000000-0000-0000-0000-000000000999"), "thumb.png");

        verify(arquivos, never()).save(any());
    }

    @Test
    @DisplayName("salvarConteudo saves bytes and publishes for images")
    void salvarConteudo_savesAndPublishes() {
        Arquivo arquivo = new Arquivo();
        arquivo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        arquivo.setMime("image/jpeg");
        arquivo.setR2Key("s3://bucket/image.jpg");

        when(arquivos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(arquivo));
        when(arquivos.save(any(Arquivo.class))).thenReturn(arquivo);

        byte[] conteudo = new byte[]{1, 2, 3};
        service.salvarConteudo(UUID.fromString("00000000-0000-0000-0000-000000000001"), conteudo);

        assertThat(arquivo.getConteudo()).isEqualTo(conteudo);
        verify(arquivos).save(arquivo);
        verify(outbox).publish(eq(EventTypes.IMAGE_UPLOADED), any(Map.class));
    }

    @Test
    @DisplayName("salvarConteudo publishes ARQUIVO_UPLOADED for image mime (storage offload applies to every file)")
    void salvarConteudo_publishesArquivoUploaded_forImage() {
        Arquivo arquivo = new Arquivo();
        arquivo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        arquivo.setMime("image/jpeg");
        arquivo.setR2Key("s3://bucket/image.jpg");

        when(arquivos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(arquivo));
        when(arquivos.save(any(Arquivo.class))).thenReturn(arquivo);

        service.salvarConteudo(UUID.fromString("00000000-0000-0000-0000-000000000001"), new byte[]{1, 2, 3});

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(eq(EventTypes.ARQUIVO_UPLOADED), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("eventId")).isEqualTo("upload-00000000-0000-0000-0000-000000000001");
        assertThat(payload.get("arquivoId")).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(payload.get("r2Key")).isEqualTo("s3://bucket/image.jpg");
    }

    @Test
    @DisplayName("salvarConteudo publishes ARQUIVO_UPLOADED for non-image mime too (unlike IMAGE_UPLOADED)")
    void salvarConteudo_publishesArquivoUploaded_forNonImage() {
        Arquivo arquivo = new Arquivo();
        arquivo.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        arquivo.setMime("application/pdf");
        arquivo.setR2Key("laudo/2/uuid-pdf");

        when(arquivos.findById(UUID.fromString("00000000-0000-0000-0000-000000000002"))).thenReturn(Optional.of(arquivo));
        when(arquivos.save(any(Arquivo.class))).thenReturn(arquivo);

        service.salvarConteudo(UUID.fromString("00000000-0000-0000-0000-000000000002"), new byte[]{9, 9});

        verify(outbox, never()).publish(eq(EventTypes.IMAGE_UPLOADED), any(Map.class));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(eq(EventTypes.ARQUIVO_UPLOADED), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("eventId")).isEqualTo("upload-00000000-0000-0000-0000-000000000002");
        assertThat(payload.get("arquivoId")).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    }

    @Test
    @DisplayName("marcarStorageOffload sets r2Key and storageProvider")
    void marcarStorageOffload_updatesArquivo() {
        Arquivo arquivo = new Arquivo();
        arquivo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        arquivo.setR2Key("laudo/1/uuid-old");

        when(arquivos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(arquivo));
        when(arquivos.save(any(Arquivo.class))).thenReturn(arquivo);

        service.marcarStorageOffload(UUID.fromString("00000000-0000-0000-0000-000000000001"), "s3", "laudo/1/uuid-old");

        assertThat(arquivo.getR2Key()).isEqualTo("laudo/1/uuid-old");
        assertThat(arquivo.getStorageProvider()).isEqualTo("s3");
        verify(arquivos).save(arquivo);
    }

    @Test
    @DisplayName("lerConteudo returns content when exists")
    void lerConteudo_returnsContent() {
        Arquivo arquivo = new Arquivo();
        arquivo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        byte[] conteudo = new byte[]{1, 2, 3, 4, 5};
        arquivo.setConteudo(conteudo);

        when(arquivos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(arquivo));

        byte[] result = service.lerConteudo(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThat(result).isEqualTo(conteudo);
    }

    @Test
    @DisplayName("lerConteudo throws NotFoundException when content is null")
    void lerConteudo_throwsNotFoundWhenNoContent() {
        Arquivo arquivo = new Arquivo();
        arquivo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        arquivo.setConteudo(null);

        when(arquivos.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(arquivo));

        assertThatThrownBy(() -> service.lerConteudo(UUID.fromString("00000000-0000-0000-0000-000000000001")))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Conteudo nao encontrado");
    }
}
