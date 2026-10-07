package br.com.vistoria.core.modules.disponibilidade;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "vistoriadores")
@Getter @Setter @NoArgsConstructor
public class Vistoriador {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false, unique = true) private UUID usuarioId;
    @Column(columnDefinition = "uuid[]") private UUID[] cidades = new UUID[0];
    private Double latBase;
    private Double lngBase;
    @Column(precision = 6, scale = 1) private BigDecimal distanciaMaxKm;
    private String plenoId;
    @Column(nullable = false) private boolean ativo = true;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String dadosPagamento = "{}";
}
