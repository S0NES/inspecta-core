package br.com.vistoria.core.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Periodo")
class PeriodoTest {

    private static final ZoneId ZONE_SP = ZoneId.of("America/Sao_Paulo");

    @Test
    @DisplayName("range parses valid YYYY-MM format")
    void range_valid_format() {
        var intervalo = Periodo.range("2024-01");

        assertThat(intervalo).isNotNull();
        assertThat(intervalo.inicio()).isNotNull();
        assertThat(intervalo.fim()).isNotNull();
    }

    @Test
    @DisplayName("range returns correct start date (first day of month at 00:00)")
    void range_start_date_first_day() {
        var intervalo = Periodo.range("2024-01");

        // January 1, 2024 00:00:00 in São Paulo time
        Instant expectedStart = YearMonth.of(2024, 1).atDay(1).atStartOfDay(ZONE_SP).toInstant();

        assertThat(intervalo.inicio()).isEqualTo(expectedStart);
    }

    @Test
    @DisplayName("range returns correct end date (first day of next month at 00:00)")
    void range_end_date_next_month() {
        var intervalo = Periodo.range("2024-01");

        // February 1, 2024 00:00:00 in São Paulo time
        Instant expectedEnd = YearMonth.of(2024, 2).atDay(1).atStartOfDay(ZONE_SP).toInstant();

        assertThat(intervalo.fim()).isEqualTo(expectedEnd);
    }

    @Test
    @DisplayName("range interval is approximately 30-31 days")
    void range_interval_duration() {
        var intervalo = Periodo.range("2024-01");

        long differenceInMillis = intervalo.fim().toEpochMilli() - intervalo.inicio().toEpochMilli();
        long differenceInDays = differenceInMillis / (24 * 60 * 60 * 1000);

        assertThat(differenceInDays).isBetween(28L, 32L); // Most months are 28-31 days
    }

    @Test
    @DisplayName("range handles February in non-leap year correctly")
    void range_february_non_leap_year() {
        var intervalo = Periodo.range("2023-02");

        Instant expectedStart = YearMonth.of(2023, 2).atDay(1).atStartOfDay(ZONE_SP).toInstant();
        Instant expectedEnd = YearMonth.of(2023, 3).atDay(1).atStartOfDay(ZONE_SP).toInstant();

        assertThat(intervalo.inicio()).isEqualTo(expectedStart);
        assertThat(intervalo.fim()).isEqualTo(expectedEnd);
    }

    @Test
    @DisplayName("range handles February in leap year correctly")
    void range_february_leap_year() {
        var intervalo = Periodo.range("2024-02");

        Instant expectedStart = YearMonth.of(2024, 2).atDay(1).atStartOfDay(ZONE_SP).toInstant();
        Instant expectedEnd = YearMonth.of(2024, 3).atDay(1).atStartOfDay(ZONE_SP).toInstant();

        assertThat(intervalo.inicio()).isEqualTo(expectedStart);
        assertThat(intervalo.fim()).isEqualTo(expectedEnd);
    }

    @Test
    @DisplayName("range handles December correctly (end is January of next year)")
    void range_december() {
        var intervalo = Periodo.range("2024-12");

        Instant expectedStart = YearMonth.of(2024, 12).atDay(1).atStartOfDay(ZONE_SP).toInstant();
        Instant expectedEnd = YearMonth.of(2025, 1).atDay(1).atStartOfDay(ZONE_SP).toInstant();

        assertThat(intervalo.inicio()).isEqualTo(expectedStart);
        assertThat(intervalo.fim()).isEqualTo(expectedEnd);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-01", "2024-06", "2024-12", "2000-01", "2099-12"})
    @DisplayName("range accepts valid months")
    void range_valid_months(String periodo) {
        var intervalo = Periodo.range(periodo);

        assertThat(intervalo.inicio()).isNotNull();
        assertThat(intervalo.fim()).isNotNull();
        assertThat(intervalo.inicio()).isBefore(intervalo.fim());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-13", "2024-00", "2024-1", "2024-001", "24-01", "2024/01", "01-2024", ""})
    @DisplayName("range rejects invalid formats")
    void range_invalid_format(String periodoInvalido) {
        assertThatThrownBy(() -> Periodo.range(periodoInvalido))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodo invalido")
                .hasMessageContaining("use YYYY-MM");
    }

    @Test
    @DisplayName("range with null throws IllegalArgumentException")
    void range_null_input() {
        assertThatThrownBy(() -> Periodo.range(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodo invalido");
    }

    @Test
    @DisplayName("range interval respects São Paulo timezone")
    void range_timezone_sao_paulo() {
        var intervalo = Periodo.range("2024-01");

        // Both start and end should be at 00:00 São Paulo time
        // Convert to São Paulo time and check hour of day
        java.time.ZonedDateTime startInSP = intervalo.inicio().atZone(ZONE_SP);
        java.time.ZonedDateTime endInSP = intervalo.fim().atZone(ZONE_SP);

        assertThat(startInSP.getHour()).isZero();
        assertThat(startInSP.getMinute()).isZero();
        assertThat(startInSP.getSecond()).isZero();

        assertThat(endInSP.getHour()).isZero();
        assertThat(endInSP.getMinute()).isZero();
        assertThat(endInSP.getSecond()).isZero();
    }

    @Test
    @DisplayName("range is deterministic (same input produces same output)")
    void range_deterministic() {
        var intervalo1 = Periodo.range("2024-06");
        var intervalo2 = Periodo.range("2024-06");

        assertThat(intervalo1.inicio()).isEqualTo(intervalo2.inicio());
        assertThat(intervalo1.fim()).isEqualTo(intervalo2.fim());
    }

    @Test
    @DisplayName("range with leading/trailing spaces fails")
    void range_with_spaces() {
        assertThatThrownBy(() -> Periodo.range(" 2024-01"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Periodo.range("2024-01 "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
