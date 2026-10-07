package br.com.vistoria.core.modules.disponibilidade;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "disponibilidade_excecoes")
@Getter @Setter @NoArgsConstructor
public class DisponibilidadeExcecao {
    public enum Tipo { ferias, bloqueio }
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID vistoriadorId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Tipo tipo;
    @Column(nullable = false) private LocalDate dataInicio;
    @Column(nullable = false) private LocalDate dataFim;
}
