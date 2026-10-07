package br.com.vistoria.core.shared.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_eventos")
@Getter @Setter @NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String routingKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    private Instant criadoEm = Instant.now();

    private Instant publicadoEm;

    private int tentativas = 0;

    public static OutboxEvent of(String routingKey, String payloadJson) {
        OutboxEvent e = new OutboxEvent();
        e.routingKey = routingKey;
        e.payload = payloadJson;
        return e;
    }
}
