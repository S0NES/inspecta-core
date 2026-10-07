package br.com.vistoria.core.modules.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os CHECK de {@code metrics.envios} em V22 sao copiados a mao dos enums Java que
 * espelham. Este teste faz o diff estatico (mesma abordagem de
 * {@code config.StatusEnumConstraintsTest}) para pegar drift antes do deploy.
 */
@DisplayName("V22 metrics CHECK constraints match the Java model")
class MetricsSchemaConstraintsTest {

    @Test
    @DisplayName("metrics_envios_status_ck matches StatusEnvio.dbValue()")
    void statusCheckMatchesEnum() {
        Set<String> naMigration = checkValues("metrics_envios_status_ck");
        Set<String> doEnum = Arrays.stream(StatusEnvio.values())
                .map(StatusEnvio::dbValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(naMigration).isEqualTo(doEnum);
    }

    @Test
    @DisplayName("metrics_envios_tipo_comunicacao_ck lists the four categories")
    void tipoComunicacaoCheck() {
        assertThat(checkValues("metrics_envios_tipo_comunicacao_ck"))
                .containsExactlyInAnyOrder("conta", "assinatura_laudo", "operacional", "marketing");
    }


    private Set<String> checkValues(String constraintName) {
        String sql = br.com.vistoria.core.BaselineSql.text();
        Pattern block = Pattern.compile(
                Pattern.quote(constraintName) + "\\s+CHECK\\s*\\(\\s*\\w+\\s+IN\\s*\\(([^)]*)\\)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = block.matcher(sql);
        if (!m.find()) return Set.of();
        Matcher lit = Pattern.compile("'([^']*)'").matcher(m.group(1));
        Set<String> values = new LinkedHashSet<>();
        while (lit.find()) values.add(lit.group(1));
        return values;
    }
}
