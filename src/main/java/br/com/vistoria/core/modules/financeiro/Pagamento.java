package br.com.vistoria.core.modules.financeiro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "pagamentos")
@Getter @Setter @NoArgsConstructor
public class Pagamento {
    public enum Status { criando, pendente, pago, falhou, estornado }

    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID solicitacaoId;
    @Column(nullable = false) private String provider;
    private String externalId;
    private String checkoutUrl;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal valor;
    @Column(nullable = false) private String status = Status.criando.name();
    private String eventId;
    /** PIX transparente: id no provedor, copia-e-cola, QR (data URI PNG) e expiração. */
    private String pixId;
    private String pixBrCode;
    private String pixQrBase64;
    private Instant pixExpiraEm;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
