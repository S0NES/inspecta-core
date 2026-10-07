package br.com.vistoria.core.modules.integracao;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "integracao_sync")
@Getter @Setter @NoArgsConstructor
public class IntegracaoSync {
    public enum Status { pendente, sincronizado, erro }

    /** espelho = criado na Pleno pelo inspecta-worker; manual = ID informado pelo usuario ("cadastro externo", T6.19). */
    public enum Origem { espelho, manual }

    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private String sistema = "pleno";
    @Column(nullable = false) private String recursoTipo;
    @Column(nullable = false) private UUID recursoId;
    private String externalId;
    private Instant ultimoSync;
    @Column(nullable = false) private String status = Status.pendente.name();
    private String erro;
    @Column(nullable = false) private String origem = Origem.espelho.name();
}
