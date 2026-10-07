package br.com.vistoria.core.modules.cadastro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma foto de um {@link Ambiente}. O arquivo em si (bytes/chave de storage) mora em
 * {@code arquivos} (ver {@code br.com.vistoria.core.modules.arquivo.Arquivo}) — esta
 * tabela só liga um {@link Ambiente} a um {@code Arquivo}, na ordem de exibição.
 */
@Entity @Table(name = "fotos")
@Getter @Setter @NoArgsConstructor
public class Foto {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID ambienteId;
    @Column(nullable = false) private UUID arquivoId;
    @Column(nullable = false) private int ordem = 0;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(nullable = false) private Instant atualizadoEm = Instant.now();
}
