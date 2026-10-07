package br.com.vistoria.core.modules.contestacao;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "reparos")
@Getter @Setter @NoArgsConstructor
public class Reparo {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    private UUID contestacaoId;
    private UUID solicitacaoId;
    @Column(nullable = false) private String descricao;
    @Column(nullable = false) private String status = "pendente";
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
