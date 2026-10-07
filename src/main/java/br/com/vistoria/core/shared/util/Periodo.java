package br.com.vistoria.core.shared.util;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

public final class Periodo {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private Periodo() {}

    public record Intervalo(Instant inicio, Instant fim) {}

    public static Intervalo range(String periodoYYYYMM) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(periodoYYYYMM);
        } catch (Exception e) {
            throw new IllegalArgumentException("periodo invalido, use YYYY-MM: " + periodoYYYYMM);
        }
        Instant inicio = ym.atDay(1).atStartOfDay(ZONE).toInstant();
        Instant fim = ym.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        return new Intervalo(inicio, fim);
    }
}
