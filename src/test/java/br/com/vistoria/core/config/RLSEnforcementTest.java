package br.com.vistoria.core.config;

import br.com.vistoria.core.BaselineSql;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RLS enforcement — verificação estática sobre {@code V1__baseline.sql}
 * (não há banco vivo neste módulo). Checa invariantes do schema final, não deltas
 * de migration: toda tabela com {@code ENABLE ROW LEVEL SECURITY} também tem
 * {@code FORCE} e ao menos uma policy; {@code arquivos} fica de fora de propósito.
 */
@DisplayName("RLS Enforcement in the baseline schema (T6.4)")
class RLSEnforcementTest {

    private static final String SQL = BaselineSql.text();
    private static final String SQL_NC = BaselineSql.noComments();

    @Test
    @DisplayName("toda tabela RLS-enabled também tem FORCE ROW LEVEL SECURITY")
    void allRLSTablesHaveForcedRLS() {
        Set<String> enabled = tablesMatching("ALTER TABLE\\s+(\\w+)\\s+ENABLE ROW LEVEL SECURITY");
        Set<String> forced = tablesMatching("ALTER TABLE\\s+(\\w+)\\s+FORCE ROW LEVEL SECURITY");

        assertThat(forced)
                .as("toda tabela com ENABLE RLS precisa de FORCE RLS")
                .containsAll(enabled);
    }

    @Test
    @DisplayName("imoveis/solicitacoes/contratos no bloco DO; clientes com policy própria (tenant puro)")
    void tenantTablesEnabledViaDoBlock() {
        assertThat(SQL)
                .contains("ARRAY['imoveis','solicitacoes','contratos']")
                .contains("ALTER TABLE clientes ENABLE ROW LEVEL SECURITY")
                .containsPattern("CREATE POLICY tenant_isolation ON clientes[\\s\\S]*?imobiliaria_id\\s*=\\s*ANY")
                .doesNotContain("is_global_client");
    }

    @Test
    @DisplayName("as tabelas de tenant indireto têm RLS habilitado")
    void indirectTenantTablesEnabled() {
        Set<String> enabled = tablesMatching("ALTER TABLE\\s+(\\w+)\\s+ENABLE ROW LEVEL SECURITY");

        assertThat(enabled).contains(
                "solicitacao_servicos", "laudos", "atribuicoes", "contestacoes",
                "reparos", "pagamentos", "contrato_faixas", "contrato_repasses", "signatarios");
    }

    @Test
    @DisplayName("toda tabela RLS-enabled tem ao menos uma policy")
    void everyEnabledTableHasAPolicy() {
        Set<String> enabled = tablesMatching("ALTER TABLE\\s+(\\w+)\\s+ENABLE ROW LEVEL SECURITY");
        Set<String> withPolicy = tablesMatching("CREATE POLICY\\s+\\w+\\s+ON\\s+(\\w+)");

        assertThat(withPolicy)
                .as("toda tabela RLS precisa de policy")
                .containsAll(enabled);
    }

    @Test
    @DisplayName("arquivos fica de fora de RLS de propósito (autorização na aplicação)")
    void archivesExcludedFromRLS() {
        assertThat(SQL).contains("arquivos");
        assertThat(tablesMatching("ALTER TABLE\\s+(\\w+)\\s+ENABLE ROW LEVEL SECURITY"))
                .doesNotContain("arquivos");
    }

    @Test
    @DisplayName("RLS multi-tenant: current_tenant_ids() lê app.current_tenants e testa pertencimento")
    void multiTenantHelperAndPolicies() {
        assertThat(SQL)
                .contains("CREATE OR REPLACE FUNCTION public.current_tenant_ids()")
                .contains("app.current_tenants")
                .contains("public.current_tenant_ids() IS NULL")
                .contains("= ANY (public.current_tenant_ids())")
                .contains("ARRAY['imoveis','solicitacoes','contratos']");
    }

    @Test
    @DisplayName("cidades.uf é VARCHAR (Hibernate espera varchar)")
    void cidadesUfIsVarchar() {
        assertThat(SQL).containsPattern("uf\\s+VARCHAR\\(2\\)");
    }


    @Test
    @DisplayName("current_tenant_ids() retorna uuid[] (nao mais bigint[])")
    void v6_recreatesFunctionAsUuidArray() {
        assertThat(SQL)
                .contains("CREATE OR REPLACE FUNCTION public.current_tenant_ids() RETURNS uuid[]")
                .contains("::uuid[]")
                .doesNotContainPattern("RETURNS\\s+bigint\\[\\]");
    }

    @Test
    @DisplayName("V6 recria as policies das 4 tabelas com imobiliaria_id proprio (via DO block dinamico)")
    void v6_recreatesDirectTenantPolicies() {
        assertThat(SQL)
                .contains("ARRAY['imoveis','solicitacoes','contratos']")
                .containsPattern("CREATE POLICY tenant_isolation ON %I[\\s\\S]*?imobiliaria_id\\s*=\\s*ANY");
    }

    @Test
    @DisplayName("V6 recria as policies das tabelas de tenant indireto e das novas tabelas, no padrao current_tenant_ids()")
    void v6_recreatesIndirectAndNewPolicies() {
        Set<String> withPolicy = tablesMatching(SQL,
                "CREATE POLICY\\s+\\w+\\s+ON\\s+(\\w+)");

        assertThat(withPolicy).contains(
                "solicitacao_servicos", "laudos", "atribuicoes", "contestacoes",
                "reparos", "pagamentos", "contrato_faixas", "contrato_repasses",
                "signatarios", "pdf_templates", "contrato_servico_grupos",
                "ambientes", "fotos");
    }

    @Test
    @DisplayName("pdf_templates e contrato_servico_grupos migram do current_setting(...)::bigint escalar para current_tenant_ids()")
    void v6_upgradesScalarPoliciesToFunction() {
        String v6 = SQL;
        assertThat(v6).doesNotContainPattern("current_setting\\('app\\.current_tenant',\\s*true\\)::bigint");
    }

    @Test
    @DisplayName("ambientes e fotos nascem com RLS habilitado e forcado (novas tabelas, UUID nativo)")
    void v6_newTablesHaveForcedRls() {
        Set<String> enabled = tablesMatching(SQL,
                "ALTER TABLE\\s+(\\w+)\\s+ENABLE ROW LEVEL SECURITY");
        Set<String> forced = tablesMatching(SQL,
                "ALTER TABLE\\s+(\\w+)\\s+FORCE ROW LEVEL SECURITY");

        assertThat(enabled).contains("ambientes", "fotos");
        assertThat(forced).contains("ambientes", "fotos");
    }

    @Test
    @DisplayName("ambientes deriva o tenant de imoveis.imobiliaria_id (1 hop); fotos, de ambientes -> imoveis (2 hops)")
    void v6_newTablesDeriveTenantThroughImovel() {
        String v6 = SQL;
        assertThat(v6).containsPattern(
                "CREATE POLICY tenant_isolation ON ambientes[\\s\\S]*?imoveis[\\s\\S]*?imobiliaria_id\\s*=\\s*ANY");
        assertThat(v6).containsPattern(
                "CREATE POLICY tenant_isolation ON fotos[\\s\\S]*?ambientes[\\s\\S]*?imoveis[\\s\\S]*?imobiliaria_id\\s*=\\s*ANY");
    }

    private Set<String> tablesMatching(String sql, String regex) {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(sql);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private Set<String> tablesMatching(String regex) {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(SQL_NC);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
