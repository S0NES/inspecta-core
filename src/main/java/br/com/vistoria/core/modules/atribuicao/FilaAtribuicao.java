package br.com.vistoria.core.modules.atribuicao;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "fila_atribuicao")
@Getter @Setter @NoArgsConstructor
public class FilaAtribuicao {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false, unique = true) private UUID servicoId;
    @Column(nullable = false) private int tentativas = 0;
    @Column(nullable = false) private Instant proximaTentativa;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
