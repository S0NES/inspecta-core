package br.com.vistoria.core.shared.webhook;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "webhooks_recebidos")
@Getter @Setter @NoArgsConstructor
public class WebhookRecebido {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private String origem;
    @Column(nullable = false, unique = true) private String eventId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;
    @Column(nullable = false) private boolean processado = false;
    @Column(nullable = false, updatable = false) private Instant recebidoEm = Instant.now();
}
