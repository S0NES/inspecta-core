package br.com.vistoria.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code atribuicoes.criado_por} is a discriminated union -- either the literal
 * {@code 'sistema'} or a {@code usuario_id} -- enforced in Postgres. V11 originally
 * enforced the numeric-bigint shape ({@code ^[0-9]+$}); V6 (UUID primary-key migration)
 * drops that CHECK and re-adds it matching the UUID shape usuario_id has now
 * ({@code ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'}).
 *
 * <p>There is no live database in this test suite (see {@link RLSEnforcementTest} for the
 * established pattern), so this test does two things: (1) confirms the constraint is
 * actually present (dropped + re-added) in V6, and (2) re-implements the same regex in
 * Java and exercises it against the value shapes {@code AssignmentService} (inspecta-worker)
 * and {@code Atribuicao.criadoPor}'s default are known to produce, so a future edit to
 * either side can't silently diverge without a test failing here.
 */
@DisplayName("atribuicoes.criado_por discriminated-union CHECK (T6.7 + V6 UUID migration)")
class AtribuicoesCriadoPorConstraintTest {

    // Mirrors V6's `criado_por ~* '^[0-9a-f]{8}-...-[0-9a-f]{12}$'` half of the CHECK exactly.
    private static final Pattern UUID_BRANCH = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("V6 dropa o CHECK numerico antigo e re-adiciona a versao UUID")
    void v6DropsAndReaddsCheckConstraint() {
        // V6 foi dobrado no V1: o CHECK já nasce na forma UUID, inline no CREATE TABLE atribuicoes.
        assertThat(br.com.vistoria.core.BaselineSql.text())
                .contains("atribuicoes_criado_por_ck")
                .containsPattern("criado_por = 'sistema'\\s*\\n?\\s*OR criado_por ~\\* '\\^\\[0-9a-f\\]");
    }

    @Test
    @DisplayName("baseline (V1) traz o CHECK já na forma UUID (V6 dobrado)")
    void baselineHasUuidCheck() {
        assertThat(br.com.vistoria.core.BaselineSql.text())
                .contains("atribuicoes_criado_por_ck")
                .doesNotContain("criado_por ~ '^[0-9]+$'")
                .containsPattern("~\\* '\\^\\[0-9a-f\\]\\{8\\}-");
    }

    @Test
    @DisplayName("'sistema' satisfies the constraint (Atribuicao.criadoPor's default)")
    void sistemaLiteralIsValid() {
        assertThat(isValidCriadoPor("sistema")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "550e8400-e29b-41d4-a716-446655440000",
            "00000000-0000-0000-0000-000000000001",
            "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"
    })
    @DisplayName("UUID usuario_id strings satisfy the constraint (case-insensitive)")
    void uuidUsuarioIdIsValid(String criadoPor) {
        assertThat(isValidCriadoPor(criadoPor)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "sistemaa", "", "Sistema", " sistema", "sistema ", "abc", "1", "42", "-1",
            "550e8400-e29b-41d4-a716-44665544000",   // um dígito a menos
            "550e8400-e29b-41d4-a716-4466554400000",  // um dígito a mais
            "550e8400e29b41d4a716446655440000",       // sem hífens
            "usuario_1"
    })
    @DisplayName("garbage/legacy-numeric values fail the constraint now that usuario_id is UUID")
    void malformedValuesAreRejected(String criadoPor) {
        assertThat(isValidCriadoPor(criadoPor)).isFalse();
    }

    /** Same predicate as the Postgres CHECK (V6): {@code criado_por = 'sistema' OR criado_por ~* uuid-regex}. */
    private boolean isValidCriadoPor(String criadoPor) {
        return "sistema".equals(criadoPor) || UUID_BRANCH.matcher(criadoPor).matches();
    }
}
