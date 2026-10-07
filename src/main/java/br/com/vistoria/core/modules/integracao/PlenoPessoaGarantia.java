package br.com.vistoria.core.modules.integracao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Pessoa/cliente fictício criado na Pleno só para viabilizar a criação da
 * imobiliária lá (a Pleno exige `pes_codigo` -> `cli_codigo`). Guardado no Inspecta
 * "para garantia" (rastreabilidade) e como chave de idempotência do espelhar.
 *
 * <p>Escrito pelo inspecta-worker em `PlenoWriteClientHttp.espelharImobiliaria`.
 * `cli_codigo` é o mesmo valor gravado em `integracao_sync.external_id` da
 * imobiliária.</p>
 */
@Entity
@Table(name = "pleno_pessoa_garantia")
@Getter @Setter @NoArgsConstructor
public class PlenoPessoaGarantia {

    @Id private UUID imobiliariaId;
    @Column(nullable = false) private String pesCodigo;
    @Column(nullable = false) private String cliCodigo;
    @Column(nullable = false) private String nome;
    @Column(nullable = false) private String cpf;
    @Column(nullable = false) private String email;
    @Column(nullable = false) private String telefone;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
