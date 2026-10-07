package br.com.vistoria.core.modules.solicitacao;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "solicitacao_servicos")
@Getter @Setter @NoArgsConstructor
public class SolicitacaoServico {
    public enum Status { pendente, atribuida, agendada, em_execucao, concluida, cancelada }
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID solicitacaoId;
    @Column(nullable = false) private UUID tipoServicoId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.pendente;
    private UUID vistoriadorId;
    /** V3: data/hora que o solicitante deseja; a atribuição pós-pagamento procura vistoriador para esta janela. */
    private Instant dataDesejada;
    private Instant dataAgendada;
    private Integer duracaoEstimadaMin;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal precoSnapshot;
    @Column(precision = 12, scale = 2) private BigDecimal repasseSnapshot;
    @Column(nullable = false) private boolean precoManual = false;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();

    /** SKU nosso — {@code UUID.randomUUID()} gerado no create; vai como {@code externalId} ao provider. */
    @Column(name = "product_id") private UUID productId;
    /** Id do produto no provider de pagamento (AbacatePay {@code data.id}); preenchido pelo inspecta-worker. */
    @Column(name = "external_product_id") private String externalProductId;

    /** B2C (Página 4): qual b2c_service esta linha representa; NULL em toda linha B2B. */
    @Column(name = "b2c_service_id") private UUID b2cServiceId;
}
