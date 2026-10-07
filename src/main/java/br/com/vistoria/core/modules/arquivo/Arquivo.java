package br.com.vistoria.core.modules.arquivo;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "arquivos")
@Getter @Setter @NoArgsConstructor
public class Arquivo {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(name = "r2_key", nullable = false, unique = true) private String r2Key;
    private String thumbKey;
    private String tipo;
    private String mime;
    private Long tamanho;
    private String ownerType;
    private UUID ownerId;
    @Column(nullable = false) private boolean processado = false;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(columnDefinition = "bytea") private byte[] conteudo;
    /** Null = ainda nao ofertado a storage externa (S3/local); dobra como flag de idempotencia. */
    private String storageProvider;
}
