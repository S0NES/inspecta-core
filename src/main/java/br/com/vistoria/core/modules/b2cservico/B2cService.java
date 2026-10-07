package br.com.vistoria.core.modules.b2cservico;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Catálogo dos serviços que um Cliente B2C pode ser habilitado a solicitar
 * (ex.: "Vistoria de Entrada"). Substitui o antigo enum hardcoded
 * {@code client_service} — tabela pequena, fácil de adicionar linha sem
 * precisar de deploy (ver {@link B2cServicoService#criar}).
 */
@Entity
@Table(name = "b2c_service")
@Getter @Setter @NoArgsConstructor
public class B2cService {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String descricao = "";

    @Column(nullable = false)
    private String icone = "";

    /** Aponta para o {@code tipos_servico} usado em {@code solicitacao_servicos.tipo_servico_id}. */
    @Column(nullable = false)
    private UUID tipoServicoId;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private Short ordem = 0;

    @Column(nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
