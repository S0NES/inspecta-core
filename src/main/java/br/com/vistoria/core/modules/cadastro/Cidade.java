package br.com.vistoria.core.modules.cadastro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

@Entity @Table(name = "cidades")
@Getter @Setter @NoArgsConstructor
public class Cidade {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private String nome;
    @Column(nullable = false, length = 2) private String uf;
    private Double lat;
    private Double lng;
}
