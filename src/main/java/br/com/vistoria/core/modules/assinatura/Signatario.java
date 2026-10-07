package br.com.vistoria.core.modules.assinatura;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "signatarios")
@Getter @Setter @NoArgsConstructor
public class Signatario {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private String documentoTipo;
    @Column(nullable = false) private UUID documentoId;
    @Column(nullable = false) private String nome;
    @Column(nullable = false) private String email;
    private String papel;
    @Column(nullable = false) private String statusAssinatura = "pendente";
    private String externalSignerId;
    private String externalDocumentId;
    private String signingUrl;
    /** Quando o Signatario foi criado (= quando a assinatura foi solicitada). Base da janela de 7 dias. */
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant criadoEm;
    /** Setado quando chega signature.accepted. */
    private Instant assinadoEm;
    /** Última vez que o cron enviou o e-mail de lembrete pra este signatario. */
    private Instant lembreteEnviadoEm;
}
