package br.com.vistoria.core.modules.notify;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "entregas", schema = "notify")
@Getter @Setter @NoArgsConstructor
public class Entrega {
    public enum Status { enviando, enviado, falhou }

    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false, unique = true) private String idempotencyKey;
    @Column(nullable = false) private String canal;
    @Column(nullable = false) private String destinatario;
    @Column(nullable = false) private String templateId;
    private UUID tenantId;
    @Column(nullable = false) private String status = Status.enviando.name();
    private String externalId;
    @Column(nullable = false) private int tentativas = 0;
    private String erro;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(nullable = false) private Instant atualizadoEm = Instant.now();
}
