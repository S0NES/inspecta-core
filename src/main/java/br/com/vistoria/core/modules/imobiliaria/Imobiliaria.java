package br.com.vistoria.core.modules.imobiliaria;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "imobiliarias")
@Getter @Setter @NoArgsConstructor
public class Imobiliaria {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false)
    private String razaoSocial;

    private String nomeFantasia;

    @Column(nullable = false, unique = true, length = 18)
    private String cnpj;

    private String pix;
    private String emailFinanceiro;
    private String telefone;

    // Mesma convenção do imóvel; cidade_id é FK para cidades (praças atendidas).
    // Nullable no schema — a obrigatoriedade fica na UI/serviço. NUNCA vai para
    // payload de espelhamento Pleno (a pessoa da imobiliária na Pleno é fictícia).
    @Column(length = 9)
    private String cep;
    @Column(length = 255)
    private String endereco;
    @Column(length = 20)
    private String numero;
    @Column(length = 120)
    private String complemento;
    @Column(name = "cidade_id")
    private UUID cidadeId;

    /**
     * Vinculo explicito opcional a uma linha de {@code pdf_templates}. {@code null} = resolucao
     * por chave (template da imobiliaria -> global -> embutido). Quando setado, o laudo usa
     * esse template especifico (ver {@code PdfTemplateService.resolverParaImobiliaria}).
     */
    @Column(name = "pdf_template_id")
    private UUID pdfTemplateId;

    /**
     * Id do customer no provider de pagamento (AbacatePay). Criado de forma assíncrona junto
     * com a imobiliária ({@code jobs.payment.customer-create}) e removido na exclusão
     * ({@code jobs.payment.customer-delete}). {@code null} enquanto o provider não confirmou —
     * o checkout é criado sem {@code customerId} nesse caso.
     */
    @Column(name = "external_payment_id")
    private String externalPaymentId;

    @Column(nullable = false)
    private boolean ativa = true;

    @Column(nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
