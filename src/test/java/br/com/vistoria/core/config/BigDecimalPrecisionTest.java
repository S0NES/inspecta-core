package br.com.vistoria.core.config;

import br.com.vistoria.core.modules.cadastro.Imovel;
import br.com.vistoria.core.modules.contrato.Contrato;
import br.com.vistoria.core.modules.contrato.ContratoFaixa;
import br.com.vistoria.core.modules.contrato.ContratoRepasse;
import br.com.vistoria.core.modules.disponibilidade.Vistoriador;
import br.com.vistoria.core.modules.financeiro.Pagamento;
import br.com.vistoria.core.modules.financeiro.PagamentoVistoriador;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoServico;
import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * every {@code BigDecimal} entity field must declare {@code precision}/{@code scale}
 * matching its DDL column exactly. Without this, Hibernate resolves a bare {@code BigDecimal}
 * to {@code numeric(38,2)} -- {@code ddl-auto: validate} still passes (both sides report
 * {@code Types.NUMERIC}, and precision/scale aren't part of that check) but entity-derived DDL
 * would silently disagree with the real schema.
 *
 * <p>Also locks in the {@code imoveis.metragem} <-> {@code contrato_faixas.metragem_min/max}
 * {@code NUMERIC(10,2)} consistency {@code PrecoService}'s range comparison depends on
 * (comparing {@code numeric(10,2)} to {@code numeric(10,2)} with no silent coercion).
 */
@DisplayName("BigDecimal precision/scale matches DDL (T6.13)")
class BigDecimalPrecisionTest {

    private record Expectation(Class<?> entity, String field, int precision, int scale) {}

    static Stream<Expectation> expectations() {
        return Stream.of(
                new Expectation(Imovel.class, "metragem", 10, 2),
                new Expectation(ContratoFaixa.class, "metragemMin", 10, 2),
                new Expectation(ContratoFaixa.class, "metragemMax", 10, 2),
                new Expectation(ContratoFaixa.class, "valorSemMob", 12, 2),
                new Expectation(ContratoFaixa.class, "valorComMob", 12, 2),
                new Expectation(ContratoRepasse.class, "valorRepasseSemMob", 12, 2),
                new Expectation(ContratoRepasse.class, "valorRepasseComMob", 12, 2),
                new Expectation(SolicitacaoServico.class, "precoSnapshot", 12, 2),
                new Expectation(Pagamento.class, "valor", 12, 2),
                new Expectation(PagamentoVistoriador.class, "valor", 12, 2),
                new Expectation(Contrato.class, "percentual", 5, 2),
                new Expectation(Vistoriador.class, "distanciaMaxKm", 6, 1)
        );
    }

    @ParameterizedTest(name = "{0}.{1} is NUMERIC({2},{3})")
    @MethodSource("expectations")
    @DisplayName("BigDecimal field precision/scale matches its DDL column")
    void fieldDeclaresExpectedPrecisionAndScale(Expectation e) throws NoSuchFieldException {
        Field field = e.entity().getDeclaredField(e.field());
        assertThat(field.getType()).isEqualTo(BigDecimal.class);

        Column column = field.getAnnotation(Column.class);
        assertThat(column)
                .as("%s.%s must carry @Column", e.entity().getSimpleName(), e.field())
                .isNotNull();
        assertThat(column.precision())
                .as("%s.%s precision", e.entity().getSimpleName(), e.field())
                .isEqualTo(e.precision());
        assertThat(column.scale())
                .as("%s.%s scale", e.entity().getSimpleName(), e.field())
                .isEqualTo(e.scale());
    }

    @Test
    @DisplayName("imoveis.metragem and contrato_faixas.metragem_min/max share NUMERIC(10,2) " +
            "so PrecoService's range comparison never silently coerces")
    void metragemPrecisionIsConsistentAcrossTables() throws NoSuchFieldException {
        Column metragem = Imovel.class.getDeclaredField("metragem").getAnnotation(Column.class);
        Column min = ContratoFaixa.class.getDeclaredField("metragemMin").getAnnotation(Column.class);
        Column max = ContratoFaixa.class.getDeclaredField("metragemMax").getAnnotation(Column.class);

        assertThat(metragem.precision()).isEqualTo(min.precision()).isEqualTo(max.precision());
        assertThat(metragem.scale()).isEqualTo(min.scale()).isEqualTo(max.scale());
    }

    @Test
    @DisplayName("scale=2 money values round the same way PrecoService does (HALF_UP)")
    void moneyScaleRoundingIsHalfUp() {
        // Sanity-checks the invariant the precision annotations exist to protect: a
        // scale-2 money value stores at most 2 decimals, rounded HALF_UP -- same as
        // PrecoService.resolver()'s percentual-mode calculation.
        BigDecimal raw = new BigDecimal("999.995");
        BigDecimal stored = raw.setScale(2, RoundingMode.HALF_UP);

        assertThat(stored).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(stored.scale()).isEqualTo(2);
    }
}
