package br.com.vistoria.core.modules.metrics;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Rollup diario D-1 de envios. Grao = (dia de data_criacao, tipo_envio, tipo_comunicacao).
 * Escrito so pelo upsert nativo de {@code MetricsRepositories.EnvioDiarioRepository}.
 */
@Entity @Table(name = "envios_diario", schema = "metrics")
@IdClass(MetricaEnvioDiario.Pk.class)
@Getter @Setter @NoArgsConstructor
public class MetricaEnvioDiario {

    @Id private LocalDate dia;
    @Id private String tipoEnvio;
    @Id private String tipoComunicacao;

    @Column(nullable = false) private long criados;
    @Column(nullable = false) private long enviados;
    @Column(nullable = false) private long entregues;
    @Column(nullable = false) private long aberturas;
    @Column(nullable = false) private long cliques;
    @Column(nullable = false) private long bounces;
    @Column(nullable = false) private long reclamacoes;
    @Column(nullable = false) private long falhas;
    @Column(nullable = false) private Instant atualizadoEm = Instant.now();

    @Getter @Setter @NoArgsConstructor
    public static class Pk implements Serializable {
        private LocalDate dia;
        private String tipoEnvio;
        private String tipoComunicacao;

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(dia, pk.dia)
                    && Objects.equals(tipoEnvio, pk.tipoEnvio)
                    && Objects.equals(tipoComunicacao, pk.tipoComunicacao);
        }

        @Override public int hashCode() {
            return Objects.hash(dia, tipoEnvio, tipoComunicacao);
        }
    }
}
