package br.com.vistoria.core.modules.notify;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "webhook_eventos", schema = "notify",
        uniqueConstraints = @UniqueConstraint(columnNames = {"broker", "event_id"}))
@Getter @Setter @NoArgsConstructor
public class NotifyWebhookEvento {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private String broker;
    @Column(nullable = false, name = "event_id") private String eventId;
    @Column(nullable = false, updatable = false) private Instant recebidoEm = Instant.now();
}
