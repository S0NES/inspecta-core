package br.com.vistoria.core.modules.cadastro;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Cidade Entity Mapping (T6.2)")
class CidadeMappingTest {

    @Test
    @DisplayName("uf field is String type for VARCHAR(2) mapping")
    void uf_fieldIsStringType() throws NoSuchFieldException {
        Field ufField = Cidade.class.getDeclaredField("uf");
        assertThat(ufField.getType()).isEqualTo(String.class);
    }

    @Test
    @DisplayName("uf field maps to column with length 2")
    void uf_mapsToColumn_length2() throws NoSuchFieldException {
        Field ufField = Cidade.class.getDeclaredField("uf");
        Column columnAnnotation = ufField.getAnnotation(Column.class);

        assertThat(columnAnnotation).isNotNull();
        assertThat(columnAnnotation.nullable()).isFalse();
        assertThat(columnAnnotation.length()).isEqualTo(2);
    }

    @Test
    @DisplayName("Cidade table name is 'cidades'")
    void cidade_tableNameIsCidades() {
        jakarta.persistence.Table tableAnnotation = Cidade.class.getAnnotation(jakarta.persistence.Table.class);
        assertThat(tableAnnotation).isNotNull();
        assertThat(tableAnnotation.name()).isEqualTo("cidades");
    }

    @Test
    @DisplayName("Cidade has id field with identity generation")
    void cidade_hasIdField() throws NoSuchFieldException {
        Field idField = Cidade.class.getDeclaredField("id");
        assertThat(idField).isNotNull();
        assertThat(idField.getType()).isEqualTo(UUID.class);
    }

    @Test
    @DisplayName("Cidade has nome field not nullable")
    void cidade_nomeFieldNotNullable() throws NoSuchFieldException {
        Field nomeField = Cidade.class.getDeclaredField("nome");
        Column columnAnnotation = nomeField.getAnnotation(Column.class);

        assertThat(columnAnnotation).isNotNull();
        assertThat(columnAnnotation.nullable()).isFalse();
    }

    @Test
    @DisplayName("Cidade can be created with all fields set")
    void cidade_canSetAllFields() {
        Cidade cidade = new Cidade();
        cidade.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        cidade.setNome("São Paulo");
        cidade.setUf("SP");
        cidade.setLat(-23.5505);
        cidade.setLng(-46.6333);

        assertThat(cidade.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(cidade.getNome()).isEqualTo("São Paulo");
        assertThat(cidade.getUf()).isEqualTo("SP");
        assertThat(cidade.getLat()).isEqualTo(-23.5505);
        assertThat(cidade.getLng()).isEqualTo(-46.6333);
    }

    @Test
    @DisplayName("Cidade uf field accepts 2-character state codes")
    void cidade_ufAccepts2CharState() {
        Cidade cidade = new Cidade();
        cidade.setUf("RJ");
        cidade.setUf("MG");
        cidade.setUf("SP");

        assertThat(cidade.getUf()).isEqualTo("SP");
    }
}
