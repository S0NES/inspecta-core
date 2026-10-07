package br.com.vistoria.core.modules.contestacao;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "contestacoes")
@Getter @Setter @NoArgsConstructor
public class Contestacao {
    public enum Status { aberta, aceita, rejeitada }
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID laudoId;
    @Column(nullable = false) private UUID nodeUuid;
    private UUID autorId;
    @Column(nullable = false) private String texto;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.aberta;
    private String resolucao;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
