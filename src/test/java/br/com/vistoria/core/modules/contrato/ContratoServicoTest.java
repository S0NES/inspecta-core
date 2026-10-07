package br.com.vistoria.core.modules.contrato;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContratoServico Entity")
class ContratoServicoTest {

    @Test
    @DisplayName("ativo defaults to true")
    void ativo_defaultsToTrue() {
        ContratoServico servico = new ContratoServico();
        assertThat(servico.isAtivo()).isTrue();
    }

    @Test
    @DisplayName("can create with all fields")
    void create_withAllFields() {
        ContratoServico servico = new ContratoServico();
        servico.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        servico.setContratoId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        servico.setTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        servico.setAtivo(false);

        assertThat(servico.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(servico.getContratoId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        assertThat(servico.getTipoServicoId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        assertThat(servico.isAtivo()).isFalse();
    }

    @Test
    @DisplayName("ativo can be toggled")
    void ativo_canToggle() {
        ContratoServico servico = new ContratoServico();
        assertThat(servico.isAtivo()).isTrue();

        servico.setAtivo(false);
        assertThat(servico.isAtivo()).isFalse();

        servico.setAtivo(true);
        assertThat(servico.isAtivo()).isTrue();
    }

    @Test
    @DisplayName("table name is contrato_servicos")
    void table_nameIsContratoServicos() {
        jakarta.persistence.Table tableAnnotation = ContratoServico.class.getAnnotation(jakarta.persistence.Table.class);
        assertThat(tableAnnotation).isNotNull();
        assertThat(tableAnnotation.name()).isEqualTo("contrato_servicos");
    }

    @Test
    @DisplayName("contratoId is not nullable")
    void contratoId_notNullable() throws NoSuchFieldException {
        java.lang.reflect.Field field = ContratoServico.class.getDeclaredField("contratoId");
        jakarta.persistence.Column annotation = field.getAnnotation(jakarta.persistence.Column.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.nullable()).isFalse();
    }

    @Test
    @DisplayName("tipoServicoId is not nullable")
    void tipoServicoId_notNullable() throws NoSuchFieldException {
        java.lang.reflect.Field field = ContratoServico.class.getDeclaredField("tipoServicoId");
        jakarta.persistence.Column annotation = field.getAnnotation(jakarta.persistence.Column.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.nullable()).isFalse();
    }

    @Test
    @DisplayName("ativo is not nullable")
    void ativo_notNullable() throws NoSuchFieldException {
        java.lang.reflect.Field field = ContratoServico.class.getDeclaredField("ativo");
        jakarta.persistence.Column annotation = field.getAnnotation(jakarta.persistence.Column.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.nullable()).isFalse();
    }
}
