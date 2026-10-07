package br.com.vistoria.core.modules.contrato;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "contrato_repasses")
@Getter @Setter @NoArgsConstructor
public class ContratoRepasse {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID contratoFaixaId;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal valorRepasseSemMob;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal valorRepasseComMob;
}
