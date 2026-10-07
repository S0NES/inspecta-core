package br.com.vistoria.core.modules.metrics;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Rollup diario D-1 de solicitacoes. Grao = (dia de solicitacoes.criado_em, tipo_servico_id).
 * {@code concluidas} + {@code emAndamento} contam servicos da solicitacao por status;
 * {@code cancelada} fica de fora. Escrito so pelo upsert nativo de
 * {@code MetricsRepositories.SolicitacaoDiarioRepository}.
 */
@Entity @Table(name = "solicitacoes_diario", schema = "metrics")
@IdClass(MetricaSolicitacaoDiario.Pk.class)
@Getter @Setter @NoArgsConstructor
public class MetricaSolicitacaoDiario {

    @Id private LocalDate dia;
    @Id private UUID tipoServicoId;

    @Column(nullable = false) private String tipoServicoNome;
    @Column(nullable = false) private long concluidas;
    @Column(nullable = false) private long emAndamento;
    @Column(nullable = false) private Instant atualizadoEm = Instant.now();

    @Getter @Setter @NoArgsConstructor
    public static class Pk implements Serializable {
        private LocalDate dia;
        private UUID tipoServicoId;

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(dia, pk.dia) && Objects.equals(tipoServicoId, pk.tipoServicoId);
        }

        @Override public int hashCode() {
            return Objects.hash(dia, tipoServicoId);
        }
    }
}
