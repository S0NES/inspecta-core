package br.com.vistoria.core.config;

import br.com.vistoria.core.BaselineSql;
import br.com.vistoria.core.modules.financeiro.Pagamento;
import br.com.vistoria.core.modules.integracao.IntegracaoSync;
import br.com.vistoria.core.modules.notify.Entrega;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T6.9 added CHECK constraints (V12) so status columns can no longer silently accept
 * garbage. Those CHECK lists are hand-copied from the Java enums they are meant to
 * mirror, which makes them driftable in exactly the way an ENUM type would not be: if
 * a constraint's literal list is ever wrong, the database silently rejects values the
 * application code legitimately produces.
 *
 * <p>This test parses the CHECK constraint's allowed-values list straight out of the
 * migration SQL (the same static-analysis approach {@link RLSEnforcementTest} uses,
 * since these tests have no live database to query) and diffs it against the actual
 * Java enum / literal values the owning service writes. It caught two real drifts
 * during the T1.5 test-suite pass, both fixed in {@code V18__fix_status_ck_enum_drift.sql}:
 * <ul>
 *   <li>{@code pagamentos_status_ck} allowed {@code processando}/{@code cancelado} but not
 *       {@code pendente}/{@code estornado}, even though {@code PagamentoService.aplicarCharge()}
 *       writes {@code pendente} and inspecta-worker's AbacatePay gateway writes {@code estornado}.</li>
 *   <li>{@code notify_entregas_status_ck} allowed {@code falha} but {@code Entrega.Status}
 *       and {@code NotifyService} both use {@code falhou}.</li>
 * </ul>
 */
@DisplayName("Status CHECK constraints match their Java enums (T6.9 + V18 drift fix)")
class StatusEnumConstraintsTest {

    @Test
    @DisplayName("pagamentos_status_ck (effective, post-V18) matches Pagamento.Status")
    void pagamentosStatusCheck_matchesEnum() throws Exception {
        Set<String> effective = effectiveCheckValues("pagamentos_status_ck");
        Set<String> enumValues = enumNames(Pagamento.Status.values());

        assertThat(effective)
                .as("pagamentos_status_ck must allow exactly Pagamento.Status's values")
                .isEqualTo(enumValues);
    }

    @Test
    @DisplayName("notify_entregas_status_ck (effective, post-V18) matches Entrega.Status")
    void notifyEntregasStatusCheck_matchesEnum() throws Exception {
        Set<String> effective = effectiveCheckValues("notify_entregas_status_ck");
        Set<String> enumValues = enumNames(Entrega.Status.values());

        assertThat(effective)
                .as("notify_entregas_status_ck must allow exactly Entrega.Status's values")
                .isEqualTo(enumValues);
    }

    @Test
    @DisplayName("integracao_sync_status_ck matches IntegracaoSync.Status")
    void integracaoSyncStatusCheck_matchesEnum() throws Exception {
        Set<String> effective = effectiveCheckValues("integracao_sync_status_ck");
        Set<String> enumValues = enumNames(IntegracaoSync.Status.values());

        assertThat(effective)
                .as("integracao_sync_status_ck must allow exactly IntegracaoSync.Status's values")
                .isEqualTo(enumValues);
    }

    @Test
    @DisplayName("pagamentos_vistoriador_status_ck allows the entity's default (pendente) and pago")
    void pagamentosVistoriadorStatusCheck_allowsDefaultAndPago() throws Exception {
        Set<String> values = effectiveCheckValues("pagamentos_vistoriador_status_ck");

        assertThat(values).containsExactlyInAnyOrder("pendente", "pago");
    }


    /** Valores permitidos por um CHECK, lidos do baseline (constraint já é a versão efetiva). */
    private Set<String> effectiveCheckValues(String constraintName) {
        return extractCheckValues(BaselineSql.text(), constraintName);
    }

    private Set<String> extractCheckValues(String migrationSql, String constraintName) {
        Pattern constraintBlock = Pattern.compile(
                Pattern.quote(constraintName) + "\\s+CHECK\\s*\\(\\s*\\w+\\s+IN\\s*\\(([^)]*)\\)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = constraintBlock.matcher(migrationSql);
        if (!matcher.find()) {
            return Set.of();
        }
        String valuesList = matcher.group(1);
        Pattern literal = Pattern.compile("'([^']*)'");
        Matcher literalMatcher = literal.matcher(valuesList);
        Set<String> values = new LinkedHashSet<>();
        while (literalMatcher.find()) {
            values.add(literalMatcher.group(1));
        }
        return values;
    }

    private Set<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
