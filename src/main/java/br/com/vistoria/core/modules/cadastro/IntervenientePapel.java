package br.com.vistoria.core.modules.cadastro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import java.time.Instant;

/**
 * Papel de interveniente — lookup editável SÓ pelo banco (sem CRUD HTTP). Seed:
 * {@code locador}, {@code locatario}, {@code fiador} (sem {@code testemunha}).
 * Referenciado por {@code intervenientes.papel_slug} e
 * {@code solicitacao_intervenientes.papel_slug}.
 */
@Entity @Table(name = "interveniente_papeis")
@Getter @Setter @NoArgsConstructor
public class IntervenientePapel {
    @Id private String slug;
    @Column(nullable = false) private String nome;
    @Column(nullable = false) private boolean ativo = true;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
