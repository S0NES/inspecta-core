package br.com.vistoria.core.modules.contrato;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "contratos")
@Getter @Setter @NoArgsConstructor
public class Contrato {
    public enum Modo { faixa, percentual }
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID imobiliariaId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Modo modo;
    @Column(precision = 5, scale = 2) private BigDecimal percentual;
    /** V3: piso no modo percentual — valor = max(percentual * base / 100, percentualMinimo). */
    @Column(precision = 12, scale = 2) private BigDecimal percentualMinimo;
    private Integer volumeMensal;
    @Column(precision = 12, scale = 2) private BigDecimal faturamentoMinimo;
    private LocalDate vigenciaInicio;
    private LocalDate vigenciaFim;
    @Column(nullable = false) private String status = "ativo";
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
