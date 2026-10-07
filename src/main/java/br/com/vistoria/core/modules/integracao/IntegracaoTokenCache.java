package br.com.vistoria.core.modules.integracao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "integracao_token_cache")
@Getter
@Setter
@NoArgsConstructor
public class IntegracaoTokenCache {
    @Id
    @Column(name = "chave")
    private String chave;

    @Column(nullable = false)
    private String valor;

    @Column(nullable = false)
    private Instant expiraEm;
}
