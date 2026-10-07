package br.com.vistoria.core.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tokens")
@Getter @Setter @NoArgsConstructor
public class TokenEntity {

    public enum Tipo { refresh, invite, api_key, reset_senha }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private UUID usuarioId;

    /** api_key: tenant dono da chave. As chaves legadas (e refresh/invite/reset) deixam nulo. */
    private UUID imobiliariaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    private String prefix;

    @Column(nullable = false)
    private String hash;

    /** api_key: 4 últimos caracteres do secret, para a lista mascarada (`••••1a2b`). */
    private String ultimos4;

    @Column(columnDefinition = "text[]")
    private String[] escopos = new String[0];

    @Column(nullable = false)
    private Instant expiraEm;

    @Column(nullable = false)
    private boolean revogado = false;

    @Column(nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
