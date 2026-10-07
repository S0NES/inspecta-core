package br.com.vistoria.core.modules.disponibilidade;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalTime;
import java.util.UUID;

@Entity @Table(name = "disponibilidades")
@Getter @Setter @NoArgsConstructor
public class Disponibilidade {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID vistoriadorId;
    @Column(nullable = false) private int diaSemana;
    @Column(nullable = false) private LocalTime horaInicio;
    @Column(nullable = false) private LocalTime horaFim;
}
