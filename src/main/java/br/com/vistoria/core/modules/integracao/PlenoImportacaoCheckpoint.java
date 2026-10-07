package br.com.vistoria.core.modules.integracao;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;

import java.time.Instant;

/**
 * V3: cursor incremental (linha única, {@code id=1}) do poller de importação Pleno —
 * até quando a última varredura de {@code GET /api/vistoria/solicitacao} chegou.
 * Padrão de {@code pleno_sync_checkpoint} / regra 7 de arquitetura (estado em Postgres).
 */
@Entity @Table(name = "pleno_importacao_checkpoint")
@Getter @Setter @NoArgsConstructor
public class PlenoImportacaoCheckpoint {
    @Id private Short id = 1;
    @Column(nullable = false) private Instant ultimoCheckpoint;
}
