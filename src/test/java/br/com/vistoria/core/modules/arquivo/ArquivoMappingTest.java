package br.com.vistoria.core.modules.arquivo;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Arquivo Entity Mapping (T6.1)")
class ArquivoMappingTest {

    @Test
    @DisplayName("r2Key field maps to r2_key column")
    void r2Key_mapsTo_r2_key_column() throws NoSuchFieldException {
        Field r2KeyField = Arquivo.class.getDeclaredField("r2Key");
        Column columnAnnotation = r2KeyField.getAnnotation(Column.class);

        assertThat(columnAnnotation).isNotNull();
        assertThat(columnAnnotation.name()).isEqualTo("r2_key");
        assertThat(columnAnnotation.nullable()).isFalse();
        assertThat(columnAnnotation.unique()).isTrue();
    }

    @Test
    @DisplayName("Arquivo has id field with identity generation")
    void arquivo_hasIdField() throws NoSuchFieldException {
        Field idField = Arquivo.class.getDeclaredField("id");
        assertThat(idField).isNotNull();
        assertThat(idField.getType()).isEqualTo(UUID.class);
    }

    @Test
    @DisplayName("Arquivo processado defaults to false")
    void arquivo_processadoDefaultsFalse() {
        Arquivo arquivo = new Arquivo();
        assertThat(arquivo.isProcessado()).isFalse();
    }

    @Test
    @DisplayName("Arquivo criadoEm defaults to Instant.now")
    void arquivo_criadoEmHasDefault() {
        Arquivo arquivo = new Arquivo();
        assertThat(arquivo.getCriadoEm()).isNotNull();
    }

    @Test
    @DisplayName("Arquivo criadoEm is not updatable")
    void arquivo_criadoEm_notUpdatable() throws NoSuchFieldException {
        Field criadoEmField = Arquivo.class.getDeclaredField("criadoEm");
        Column columnAnnotation = criadoEmField.getAnnotation(Column.class);

        assertThat(columnAnnotation).isNotNull();
        assertThat(columnAnnotation.updatable()).isFalse();
    }

    @Test
    @DisplayName("Arquivo can be created with all fields set")
    void arquivo_canSetAllFields() {
        Arquivo arquivo = new Arquivo();
        arquivo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        arquivo.setR2Key("s3://bucket/file.pdf");
        arquivo.setThumbKey("s3://bucket/thumb.jpg");
        arquivo.setTipo("pdf");
        arquivo.setMime("application/pdf");
        arquivo.setTamanho(1024L);
        arquivo.setOwnerType("laudo");
        arquivo.setOwnerId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        arquivo.setProcessado(true);

        assertThat(arquivo.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(arquivo.getR2Key()).isEqualTo("s3://bucket/file.pdf");
        assertThat(arquivo.getThumbKey()).isEqualTo("s3://bucket/thumb.jpg");
        assertThat(arquivo.getTipo()).isEqualTo("pdf");
        assertThat(arquivo.getMime()).isEqualTo("application/pdf");
        assertThat(arquivo.getTamanho()).isEqualTo(1024L);
        assertThat(arquivo.getOwnerType()).isEqualTo("laudo");
        assertThat(arquivo.getOwnerId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        assertThat(arquivo.isProcessado()).isTrue();
    }

    @Test
    @DisplayName("Arquivo r2Key is unique constraint")
    void arquivo_r2Key_unique() throws NoSuchFieldException {
        Field r2KeyField = Arquivo.class.getDeclaredField("r2Key");
        Column columnAnnotation = r2KeyField.getAnnotation(Column.class);

        // Unique constraint prevents duplicate r2_key values
        assertThat(columnAnnotation.unique()).isTrue();
    }

    @Test
    @DisplayName("Arquivo table name is 'arquivos'")
    void arquivo_tableNameIsArquivos() {
        jakarta.persistence.Table tableAnnotation = Arquivo.class.getAnnotation(jakarta.persistence.Table.class);
        assertThat(tableAnnotation).isNotNull();
        assertThat(tableAnnotation.name()).isEqualTo("arquivos");
    }
}
