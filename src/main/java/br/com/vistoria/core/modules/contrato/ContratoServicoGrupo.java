package br.com.vistoria.core.modules.contrato;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * V3 — quais Serviços guarda-chuva a imobiliária oferece no contrato e, no modo
 * percentual, com que percentual/mínimo por serviço. O TIPO de biletagem (faixa|percentual)
 * é único por contrato ({@code Contrato.modo}); só os VALORES variam por serviço.
 * No modo faixa os valores continuam em {@code contrato_faixas} (por tipoServicoId).
 */
@Entity @Table(name = "contrato_servico_grupos")
@Getter @Setter @NoArgsConstructor
public class ContratoServicoGrupo {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID contratoId;
    @Column(nullable = false) private UUID servicoId;
    /** modo percentual: % do aluguel para este serviço. */
    @Column(precision = 5, scale = 2) private BigDecimal percentual;
    /** modo percentual: piso; valor = max(percentual * aluguel / 100, percentualMinimo). */
    @Column(precision = 12, scale = 2) private BigDecimal percentualMinimo;
}
