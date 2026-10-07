package br.com.vistoria.core.modules.solicitacao;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/**
 * Interveniente DA SOLICITAÇÃO — quem acompanha/assina a vistoria. Substitui o
 * antigo {@code solicitacoes.acompanham TEXT[]}. É esta lista (via
 * laudo → solicitacao_servico → solicitacao) que vira os signatários da assinatura (Autentique).
 *
 * <p>Create rejeita 0 linhas. {@code cpf} guardado com 11 dígitos; {@code email}
 * trim + lower — validados por {@code br.com.vistoria.core.shared.validation.PessoaDocumento}.</p>
 */
@Entity @Table(name = "solicitacao_intervenientes")
@Getter @Setter @NoArgsConstructor
public class SolicitacaoInterveniente {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID solicitacaoId;
    @Column(name = "papel_slug", nullable = false) private String papelSlug;
    @Column(nullable = false) private String nome;
    @Column(nullable = false) private String cpf;
    @Column(nullable = false) private String email;
    /** 10-11 dígitos na gravação; opcional. */
    private String telefone;
    /** Quem acompanha a vistoria (0+; opcional). É sempre um dos intervenientes da solicitação. */
    @Column(nullable = false) private boolean acompanhante = false;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
