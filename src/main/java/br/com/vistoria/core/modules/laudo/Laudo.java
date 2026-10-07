package br.com.vistoria.core.modules.laudo;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "laudos")
@Getter @Setter @NoArgsConstructor
public class Laudo {
    public enum Status { rascunho, finalizado, assinado }

    /** V3: gerado_internamente = pipeline OpenHTML2PDF; importado_pleno = PDF baixado da Pleno. */
    public enum Origem { gerado_internamente, importado_pleno }

    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID solicitacaoServicoId;
    @Column(nullable = false) private int versao = 1;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.rascunho;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Origem origem = Origem.gerado_internamente;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    private String conteudoJson = "{\"ambientes\":[]}";
    private UUID pdfArquivoId;
    private Instant assinadoEm;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
