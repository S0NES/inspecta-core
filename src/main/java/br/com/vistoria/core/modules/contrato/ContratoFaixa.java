package br.com.vistoria.core.modules.contrato;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "contrato_faixas")
@Getter @Setter @NoArgsConstructor
public class ContratoFaixa {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID contratoId;

    /** V4 — Serviço guarda-chuva dono desta tabela de faixas (modo faixa, decisão 1-a). Nullable p/ linhas legadas. */
    @Column private UUID servicoId;
    /** V4 — qual das 5 faixas globais (faixa_referencia.id 1..5) esta linha define. Nullable p/ linhas legadas. */
    @Column private Short faixaReferenciaId;

    /** Legado: a resolução do modo faixa (V4+) ignora tipo_servico_id. Ainda usado pelo inspecta-api legado. */
    @Column private UUID tipoServicoId;
    /** V4 — nullable: o intervalo do novo modelo vem de faixa_referencia. Mantida p/ legado. */
    @Column(precision = 10, scale = 2) private BigDecimal metragemMin;
    /** V4 — nullable: o intervalo do novo modelo vem de faixa_referencia. Mantida p/ legado. */
    @Column(precision = 10, scale = 2) private BigDecimal metragemMax;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal valorSemMob;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal valorComMob;
    @Column(precision = 10, scale = 2) private BigDecimal paStepMetragem;
    @Column(precision = 12, scale = 2) private BigDecimal paStepValor;
    @Column(nullable = false) private boolean revisaoPendente = false;
}
