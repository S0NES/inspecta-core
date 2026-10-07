package br.com.vistoria.core.modules.cadastro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "imoveis")
@Getter @Setter @NoArgsConstructor
public class Imovel {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    private UUID imobiliariaId;
    @Column(nullable = false) private UUID cidadeId;
    @Column(nullable = false) private String endereco;
    private String numero;
    private String complemento;
    private String cib;
    @Column(columnDefinition = "TEXT") private String observacoes;
    private String cep;
    private Double lat;
    private Double lng;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal metragem;
    @Column(nullable = false) private boolean mobiliado = false;
    /** V3: valor do aluguel — base do cálculo de biletagem no modo percentual. */
    @Column(precision = 12, scale = 2) private BigDecimal valorAluguel;
    private String tipo;
    /** Quantidade de quartos; nulo quando o imóvel não declara quarto. */
    private Integer quartos;
    /** Se o imóvel possui cobertura. */
    @Column(nullable = false) private boolean cobertura = false;
    /** V28: imóvel inativo some das listas de seleção e não espelha para a Pleno. */
    @Column(nullable = false) private boolean ativo = true;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();

    /** Cadastro externo: imo_codigo de um imovel que ja existe na Pleno. Nao persiste — so trafega da request pro CadastroService. */
    @Transient private String plenoIdExterno;
}
