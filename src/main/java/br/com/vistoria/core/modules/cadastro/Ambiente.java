package br.com.vistoria.core.modules.cadastro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/**
 * Um cômodo do imóvel (ex.: "Sala", "Quarto 1"), usado para agrupar as {@link Foto}s
 * de uma vistoria. {@code ordem} controla a posição de exibição na UI.
 */
@Entity @Table(name = "ambientes")
@Getter @Setter @NoArgsConstructor
public class Ambiente {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID imovelId;
    @Column(nullable = false) private String nome;
    private String tipo;
    @Column(nullable = false) private int ordem = 0;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(nullable = false) private Instant atualizadoEm = Instant.now();
}
