package br.com.vistoria.core.modules.cadastro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "clientes")
@Getter @Setter @NoArgsConstructor
public class Cliente {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    private UUID imobiliariaId;
    private UUID usuarioId;
    @Column(nullable = false) private String nome;
    private String cpfCnpj;
    private String email;
    private String telefone;

    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
