package br.com.vistoria.core.modules.contrato;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

@Entity @Table(name = "contrato_servicos")
@Getter @Setter @NoArgsConstructor
public class ContratoServico {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID contratoId;
    @Column(nullable = false) private UUID tipoServicoId;
    @Column(nullable = false) private boolean ativo = true;
}
