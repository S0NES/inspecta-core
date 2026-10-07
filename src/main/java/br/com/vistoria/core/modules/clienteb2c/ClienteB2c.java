package br.com.vistoria.core.modules.clienteb2c;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cliente B2C — perfil {@code cliente}, sem imobiliária. Distinto de
 * {@link br.com.vistoria.core.modules.cadastro.Cliente} (contato B2B tenant-scoped).
 */
@Entity
@Table(name = "clientes_b2c")
@Getter @Setter @NoArgsConstructor
public class ClienteB2c {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID usuarioId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String cpf;

    @Column(nullable = false)
    private String telefone;

    @Column(nullable = false)
    private UUID cidadeId;

    /** Ids de {@code b2c_service} que este cliente pode solicitar. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client_services", columnDefinition = "jsonb", nullable = false)
    private List<UUID> clientServices = List.of();

    @Column(nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
