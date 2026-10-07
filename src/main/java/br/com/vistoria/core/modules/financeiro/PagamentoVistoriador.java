package br.com.vistoria.core.modules.financeiro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "pagamentos_vistoriador")
@Getter @Setter @NoArgsConstructor
public class PagamentoVistoriador {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID vistoriadorId;
    @Column(nullable = false) private String periodo;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal valor;
    // Matches DDL default (V3__schema_completo.sql pagamentos_vistoriador.status DEFAULT 'pendente')
    // and the pagamentos_vistoriador_status_ck CHECK ('pendente','pago'). A freshly-registered
    // payment to an inspector has not been paid yet -- was previously "pago" here, a T6.9
    // default divergence that was flagged but never actually fixed (see V18 migration note).
    @Column(nullable = false) private String status = "pendente";
    private UUID comprovanteArquivoId;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
