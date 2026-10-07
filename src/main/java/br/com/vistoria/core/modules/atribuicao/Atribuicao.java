package br.com.vistoria.core.modules.atribuicao;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "atribuicoes")
@Getter @Setter @NoArgsConstructor
public class Atribuicao {
    public enum Acao { atribuido, reatribuido, reagendado }
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID servicoId;
    @Column(nullable = false) private UUID vistoriadorId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Acao acao;
    private String motivo;
    @Column(nullable = false) private String criadoPor = "sistema";
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
