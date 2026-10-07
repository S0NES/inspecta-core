package br.com.vistoria.core.modules.metrics;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma linha por mensagem de saida (email/sms/whatsapp/push/...). Generica: as
 * dimensoes sao {@code tipoEnvio} (canal) x {@code tipoComunicacao}
 * (conta/assinatura_laudo/operacional/marketing).
 *
 * <p>Escrita so pelo inspecta-worker (consumidor de {@code q.metrics}); lida pelo
 * rollup diario. O inspecta-api nunca le esta tabela diretamente.</p>
 */
@Entity @Table(name = "envios", schema = "metrics")
@Getter @Setter @NoArgsConstructor
public class MetricaEnvio {

    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;

    /** eventId / idempotency key do evento notify — correlaciona criacao, envio e status. */
    @Column(nullable = false, unique = true) private String mensagemId;

    @Column(nullable = false) private String tipoEnvio;
    @Column(nullable = false) private String tipoComunicacao;

    private String externalId;

    @Column(nullable = false) private String status = StatusEnvio.CRIADO.dbValue();
    private String statusRaw;
    private String broker;

    @Column(nullable = false, updatable = false) private Instant dataCriacao = Instant.now();
    private Instant dataEnvio;
    private Instant statusEm;

    private UUID tenantId;

    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(nullable = false) private Instant atualizadoEm = Instant.now();

    public StatusEnvio statusEnum() {
        return StatusEnvio.fromDb(status);
    }
}
