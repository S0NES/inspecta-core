package br.com.vistoria.core.modules.cadastro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import java.math.BigDecimal;

/**
 * V3 — as 5 faixas de metragem de referência (estrutura global e fixa). Os VALORES
 * (com/sem mobília) são por imobiliária e ficam em {@code contrato_faixas}; aqui só o
 * intervalo e o rótulo. metragemMax NULL = faixa 5 ("a partir de metragemMin, sem teto").
 */
@Entity @Table(name = "faixa_referencia")
@Getter @Setter @NoArgsConstructor
public class FaixaReferencia {
    @Id private Short id;
    @Column(nullable = false, unique = true) private Short ordem;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal metragemMin;
    @Column(precision = 10, scale = 2) private BigDecimal metragemMax;
    @Column(nullable = false) private String rotulo;
}
