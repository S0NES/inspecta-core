package br.com.vistoria.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Análise estática das migrations V2–V4 (não há banco vivo neste módulo, mesmo
 * approach de {@link RLSEnforcementTest}). Trava as invariantes de schema que o
 * código Java depende, já que {@code BaselineSql} só enxerga o V1.
 */
@DisplayName("Migrations V2–V4 (endereço imobiliária / importação Pleno / B2B billing)")
class NovasMigracoesSchemaTest {

    private static String sql(String file) {
        try (InputStream is = NovasMigracoesSchemaTest.class.getClassLoader()
                .getResourceAsStream("db/migration/" + file)) {
            if (is == null) throw new IllegalStateException(file + " não está no classpath");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("V2 adiciona os 5 campos de endereço em imobiliarias, cidade_id FK para cidades")
    void v2_enderecoImobiliaria() {
        String v2 = sql("V2__imobiliaria_endereco.sql");
        assertThat(v2)
                .contains("ALTER TABLE imobiliarias")
                .containsPattern("cep\\s+VARCHAR\\(9\\)")
                .containsPattern("endereco\\s+VARCHAR\\(255\\)")
                .containsPattern("numero\\s+VARCHAR\\(20\\)")
                .containsPattern("complemento\\s+VARCHAR\\(120\\)")
                .containsPattern("cidade_id\\s+UUID REFERENCES cidades\\(id\\)");
    }

    @Test
    @DisplayName("V3: laudos.origem NOT NULL DEFAULT + CHECK; tabelas de importação sem RLS")
    void v3_plenoImportacao() {
        String v3 = sql("V3__pleno_importacao_laudo.sql");
        assertThat(v3)
                .containsPattern("ALTER TABLE laudos[\\s\\S]*?ADD COLUMN origem VARCHAR\\(20\\) NOT NULL DEFAULT 'gerado_internamente'")
                .contains("CHECK (origem IN ('gerado_internamente', 'importado_pleno'))")
                .contains("CREATE TABLE pleno_importacao_vistoria")
                .containsPattern("solicitacao_servico_id\\s+UUID NOT NULL UNIQUE REFERENCES solicitacao_servicos\\(id\\)")
                .contains("CREATE TABLE pleno_importacao_checkpoint")
                .contains("CHECK (id = 1)");
        // operacional: sem policy de tenant
        assertThat(v3).doesNotContain("ENABLE ROW LEVEL SECURITY");
    }

    @Test
    @DisplayName("V4: solicitacoes ganha valor_aluguel/mobiliado sem DROP NOT NULL; extrato + fatura com RLS por tenant")
    void v4_b2bBilling() {
        String v4 = sql("V4__solicitacao_b2b_billing.sql");
        assertThat(v4)
                .containsPattern("ALTER TABLE solicitacoes[\\s\\S]*?ADD COLUMN valor_aluguel NUMERIC\\(12,2\\)")
                .containsPattern("ADD COLUMN mobiliado\\s+BOOLEAN")
                .doesNotContain("DROP NOT NULL")
                .contains("CREATE TABLE faturas_consolidadas")
                .contains("CHECK (status IN ('pendente', 'checkout_ready', 'pago', 'erro'))")
                .contains("CREATE TABLE extrato_itens")
                .contains("CREATE INDEX idx_extrato_itens_imob_criado ON extrato_itens (imobiliaria_id, criado_em)");

        // as duas tabelas novas têm imobiliaria_id próprio -> RLS tenant direto
        for (String t : new String[]{"faturas_consolidadas", "extrato_itens"}) {
            assertThat(v4)
                    .contains("ALTER TABLE " + t + " ENABLE ROW LEVEL SECURITY")
                    .contains("ALTER TABLE " + t + " FORCE ROW LEVEL SECURITY")
                    .containsPattern("CREATE POLICY tenant_isolation ON " + t
                            + "[\\s\\S]*?imobiliaria_id = ANY \\(public.current_tenant_ids\\(\\)\\)");
        }
    }

    @Test
    @DisplayName("faturas_consolidadas é criada antes de extrato_itens (FK fatura_id)")
    void v4_ordemDeCriacao() {
        String v4 = sql("V4__solicitacao_b2b_billing.sql");
        assertThat(v4.indexOf("CREATE TABLE faturas_consolidadas"))
                .isLessThan(v4.indexOf("CREATE TABLE extrato_itens"))
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("V9: cnpj de imobiliarias normalizado para só dígitos (VARCHAR(14))")
    void v9_cnpjDigitos() {
        String v9 = sql("V9__imobiliaria_cnpj_digits.sql");
        assertThat(v9)
                .contains("UPDATE imobiliarias SET cnpj = regexp_replace(cnpj, '\\D', '', 'g')")
                .containsPattern("ALTER TABLE imobiliarias ALTER COLUMN cnpj TYPE VARCHAR\\(14\\)");
    }

    @Test
    @DisplayName("V10: tokens ganha imobiliaria_id + ultimos4 para as API keys")
    void v10_apiKeyImobiliaria() {
        String v10 = sql("V10__api_key_imobiliaria.sql");
        assertThat(v10)
                .containsPattern("ADD COLUMN imobiliaria_id UUID REFERENCES imobiliarias\\(id\\)")
                .containsPattern("ADD COLUMN ultimos4\\s+VARCHAR\\(4\\)")
                .contains("CREATE INDEX idx_tokens_imobiliaria ON tokens (imobiliaria_id)");
    }

    @Test
    @DisplayName("V11: solicitacao_intervenientes ganha acompanhante/telefone e a tabela intervenientes é derrubada")
    void v11_intervenientesSolicitacaoOnly() {
        String v11 = sql("V11__intervenientes_solicitacao_only.sql");
        assertThat(v11)
                .contains("ALTER TABLE solicitacao_intervenientes")
                .contains("ADD COLUMN acompanhante BOOLEAN NOT NULL DEFAULT FALSE")
                .contains("ADD COLUMN telefone     TEXT")
                .contains("DROP TABLE IF EXISTS intervenientes");
    }

    // helper mantido por clareza/paralelismo com RLSEnforcementTest
    @SuppressWarnings("unused")
    private static String noComments(String s) {
        return s.lines().filter(l -> !l.stripLeading().startsWith("--")).collect(Collectors.joining("\n"));
    }
}
