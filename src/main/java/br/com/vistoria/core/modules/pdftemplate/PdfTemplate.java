package br.com.vistoria.core.modules.pdftemplate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Template de PDF de uma imobiliaria (ou o global, quando {@code imobiliariaId} e nulo).
 *
 * <p>Versionado como {@code notify.templates}: uma versao publicada nao se edita, cria-se a
 * proxima. Um laudo assinado e imutavel (arquitetura.md, regra 12), entao precisa continuar
 * sendo possivel saber com qual template ele foi gerado.</p>
 *
 * <p>{@code id} era {@code BIGSERIAL} com um {@code uuid} externo estavel em coluna separada;
 * a migracao UUID promoveu esse {@code uuid} a PK (nao ha mais coluna separada).</p>
 */
@Entity
@Table(name = "pdf_templates")
@Getter
@Setter
@NoArgsConstructor
public class PdfTemplate {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    /** Tipo do documento; corresponde a um {@link TemplateChave}. */
    @Column(nullable = false)
    private String chave;

    /** Dono do template. {@code null} = template global, fallback de todas as imobiliarias. */
    private UUID imobiliariaId;

    @Column(nullable = false)
    private int versao = 1;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String html;

    private String css;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Column(nullable = false)
    private Instant atualizadoEm = Instant.now();

    /** {@code true} quando este e o template global (fallback), nao o de uma imobiliaria. */
    public boolean isGlobal() {
        return imobiliariaId == null;
    }
}
