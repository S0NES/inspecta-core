package br.com.vistoria.core.config;

import br.com.vistoria.core.BaselineSql;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ON DELETE por relação, não o {@code NO ACTION} geral.
 *
 * <p>Sem banco vivo neste módulo, a verificação é estática sobre o
 * {@code V1__baseline.sql} consolidado (V6/UUID já dobrado nele): as relações de
 * composição têm {@code ON DELETE CASCADE} inline no {@code CREATE TABLE}, e as
 * relações portadoras de identidade ({@code contestacoes -> laudos},
 * {@code solicitacao_servicos -> solicitacoes}) continuam sem cascade.</p>
 */
@DisplayName("FK ON DELETE semantics (T6.10)")
class FkOnDeleteSemanticsTest {

    @Test
    @DisplayName("tokens.usuario_id cascades on usuario delete (credential lifecycle)")
    void tokensCascadesOnUsuarioDelete() {
        assertCascades("tokens", "usuarios(id)");
    }

    @Test
    @DisplayName("contrato_faixas.contrato_id cascades on contrato delete (composition)")
    void contratoFaixasCascadesOnContratoDelete() {
        assertCascades("contrato_faixas", "contratos(id)");
    }

    @Test
    @DisplayName("contrato_repasses.contrato_faixa_id cascades on faixa delete (composition)")
    void contratoRepassesCascadesOnFaixaDelete() {
        assertCascades("contrato_repasses", "contrato_faixas(id)");
    }

    @Test
    @DisplayName("solicitacao_intervenientes.solicitacao_id cascades (filho da solicitação)")
    void solicitacaoIntervenientesCascades() {
        assertCascades("solicitacao_intervenientes", "solicitacoes(id)");
    }

    @Test
    @DisplayName("imobiliarias.pdf_template_id -> pdf_templates(id) é ON DELETE SET NULL")
    void pdfTemplateSetNull() {
        assertThat(BaselineSql.text())
                .containsPattern("imobiliarias_pdf_template_id_fkey[\\s\\S]{0,160}ON DELETE SET NULL");
    }

    @Test
    @DisplayName("contestacoes -> laudos is explicitly left NO ACTION (rule 12: signed laudo is immutable)")
    void contestacoesToLaudos_notCascaded() {
        String block = createTableBlock(BaselineSql.text(), "contestacoes");
        assertThat(refClause(block, "laudo_id", "laudos(id)"))
                .as("contestacoes.laudo_id não deve cascatear")
                .doesNotContain("ON DELETE CASCADE");
    }

    @Test
    @DisplayName("solicitacao_servicos -> solicitacoes is explicitly left NO ACTION (audit/financial trail)")
    void solicitacaoServicosToSolicitacoes_notCascaded() {
        String block = createTableBlock(BaselineSql.text(), "solicitacao_servicos");
        assertThat(refClause(block, "solicitacao_id", "solicitacoes(id)"))
                .as("solicitacao_servicos.solicitacao_id não deve cascatear")
                .doesNotContain("ON DELETE CASCADE");
    }


    private void assertCascades(String table, String referenced) {
        String block = createTableBlock(BaselineSql.text(), table);
        assertThat(block)
                .as("%s deve ON DELETE CASCADE para %s", table, referenced)
                .containsPattern("REFERENCES\\s+" + Pattern.quote(referenced) + "\\s+ON DELETE CASCADE");
    }

    /** Corpo do `CREATE TABLE <table> ( ... );`. */
    private String createTableBlock(String sql, String table) {
        Matcher m = Pattern.compile(
                "CREATE TABLE\\s+" + Pattern.quote(table) + "\\s*\\((.*?)\\n\\);",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(sql);
        assertThat(m.find()).as("CREATE TABLE %s no baseline", table).isTrue();
        return m.group(1);
    }

    /** Trecho da coluna `<col>` que referencia `<referenced>` (dentro de um bloco já delimitado). */
    private String refClause(String block, String col, String referenced) {
        Matcher m = Pattern.compile(
                Pattern.quote(col) + "[^,\\n]*REFERENCES\\s+" + Pattern.quote(referenced) + "[^,\\n]*",
                Pattern.CASE_INSENSITIVE).matcher(block);
        assertThat(m.find()).as("coluna %s -> %s no bloco", col, referenced).isTrue();
        return m.group();
    }
}
