package br.com.vistoria.core.modules.financeiro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * V4 (D3): cobrança consolidada de um período por imobiliária. É o "dono" do checkout
 * no fluxo B2B (no lugar da {@code Solicitacao} individual do fluxo reservado sem
 * imobiliária). Agrega N {@link ExtratoItem} em aberto.
 */
@Entity
@Table(name = "faturas_consolidadas", uniqueConstraints = @UniqueConstraint(
        name = "uk_faturas_consolidadas_imob_periodo",
        columnNames = {"imobiliaria_id", "periodo_inicio", "periodo_fim"}))
@Getter @Setter @NoArgsConstructor
public class FaturaConsolidada {
    public enum Status { pendente, checkout_ready, pago, erro }

    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID imobiliariaId;
    @Column(nullable = false) private LocalDate periodoInicio;
    @Column(nullable = false) private LocalDate periodoFim;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal valorTotal;
    @Column(nullable = false) private String status = Status.pendente.name();
    private String checkoutId;
    private String checkoutUrl;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
