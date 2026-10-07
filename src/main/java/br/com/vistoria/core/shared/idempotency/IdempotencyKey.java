package br.com.vistoria.core.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma linha por Idempotency-Key vista numa rota mutante /v1/* do inspecta-api.
 *
 * `statusHttp == null` = chave reivindicada mas a requisicao original ainda esta
 * em processamento (lock de corrida — a 2a requisicao concorrente recebe 409).
 * Preenchido = resposta terminal armazenada para replay.
 *
 * Sem RLS (ver V23): o tenant entra no `requestHash`, entao a mesma chave em dois
 * tenants nunca casa como HIT.
 */
@Entity
@Table(name = "idempotency_keys")
@Getter @Setter @NoArgsConstructor
public class IdempotencyKey implements Persistable<String> {

    @Id
    private String chave;

    @Column(nullable = false)
    private String metodo;

    @Column(nullable = false)
    private String path;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "status_http")
    private Integer statusHttp;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    // Persistable: garante INSERT puro em repo.save() (sem SELECT+merge), pra
    // reivindicar() detectar corrida por violacao de PK em vez de sobrescrever.
    @Transient
    private boolean novo = true;

    @Override
    public String getId() {
        return chave;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    @PostLoad
    @PostPersist
    void marcarPersistido() {
        this.novo = false;
    }
}
