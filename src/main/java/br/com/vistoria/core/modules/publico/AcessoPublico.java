package br.com.vistoria.core.modules.publico;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "acessos_publicos")
@Getter @Setter @NoArgsConstructor
public class AcessoPublico {
    public enum Tipo { laudo, imagens }
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false, unique = true) private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Tipo tipo;
    @Column(nullable = false) private UUID recursoId;
    @Column(nullable = false) private Instant expiraEm;
    @Column(nullable = false) private boolean revogado = false;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
