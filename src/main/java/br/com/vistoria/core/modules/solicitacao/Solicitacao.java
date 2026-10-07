package br.com.vistoria.core.modules.solicitacao;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "solicitacoes")
@Getter @Setter @NoArgsConstructor
public class Solicitacao {
    public enum Origem { painel, portal, api }
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    private UUID imobiliariaId;
    // Opcional (V24): a solicitacao B2B junta imovel + imobiliaria + servicos; o
    // cliente-pessoa deixou de ser obrigatorio. Quando != null, referencia clientes(id).
    private UUID clienteId;
    /** B2C (Página 4): referencia clientes_b2c(id). Nunca preenchido junto com clienteId/imobiliariaId. */
    @Column(name = "cliente_b2c_id") private UUID clienteB2cId;
    @Column(nullable = false) private UUID imovelId;
    @Column(nullable = false) private boolean urgente = false;
    private String chavesRetirada;
    private String chavesDevolucao;
    // V4 (D2): aluguel/mobiliado deixam de ser atributo de verdade do imóvel para
    // precificação — viram snapshot da solicitação (mesmo valor p/ todos os serviços).
    // Obrigatórios (validação de serviço) só quando há imobiliária e contrato percentual.
    @Column(name = "valor_aluguel", precision = 12, scale = 2) private BigDecimal valorAluguel;
    private Boolean mobiliado;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Origem origem;
    private UUID criadoPor;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();

    // Um checkout por solicitação (itens = os produtos de cada solicitacao_servico).
    // paymentStatus: nao_iniciado -> checkout_ready -> pago | erro. A atribuição de
    // vistoriador só é publicada quando paymentStatus vira 'pago' (webhook confirmado).
    @Column(name = "checkout_id") private String checkoutId;
    @Column(name = "checkout_url") private String checkoutUrl;
    @Column(name = "payment_status", nullable = false) private String paymentStatus = "nao_iniciado";

    /** Derivado: a UI só mostra o botão "Pagar" quando há checkout e ainda não foi pago. */
    @Transient
    public boolean isCheckoutReady() {
        return checkoutUrl != null && !"pago".equals(paymentStatus);
    }
}
