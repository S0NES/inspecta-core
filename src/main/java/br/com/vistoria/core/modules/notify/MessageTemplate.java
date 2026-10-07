package br.com.vistoria.core.modules.notify;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Template de mensagem (e-mail/SMS/push) identificado por um UUID estável.
 *
 * <p>Fonte de verdade dos templates de mensagem (a antiga {@code notify.templates} foi removida
 * na migracao UUID). O {@code uuid} e o identificador
 * logico — o que vai no evento {@code notify.requested.*} e no env; {@code (uuid, versao)} e a
 * PK, porque uma versao publicada nunca e sobrescrita (mesmo motivo de {@code pdf_templates}:
 * precisa continuar sendo possivel saber com que texto uma entrega foi feita).</p>
 *
 * <p>Implementa {@link Persistable}: sem isso, o {@code @IdClass} com {@code versao} primitivo
 * (nunca nulo) fazia o Spring Data considerar toda instância como "já existente" e rotear o
 * {@code save()} por {@code merge()} — a operação errada para inserir uma linha nova
 * {@code (uuid, versao)}, e a origem do 500 na criação de template. {@link #isNew()} volta a
 * dizer a verdade: {@code true} numa instância recém-construída, {@code false} depois de
 * carregada ou persistida.</p>
 */
@Entity
@Table(name = "message_templates", schema = "notify")
@IdClass(MessageTemplate.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class MessageTemplate implements Persistable<MessageTemplate.Pk> {

    public static class Pk implements Serializable {
        public UUID uuid;
        public int versao;
        public Pk() {}
        public Pk(UUID uuid, int versao) { this.uuid = uuid; this.versao = versao; }
        @Override public boolean equals(Object o) {
            return o instanceof Pk p && Objects.equals(uuid, p.uuid) && versao == p.versao;
        }
        @Override public int hashCode() { return Objects.hash(uuid, versao); }
    }

    @Id private UUID uuid;
    @Id private int versao = 1;

    @Column(nullable = false) private String canal;
    @Column(nullable = false) private String nome;
    private String assunto;
    @Column(nullable = false) private String corpo;
    @Column(nullable = false) private boolean ativo = true;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(nullable = false) private Instant atualizadoEm = Instant.now();

    /**
     * Só existe em memória: {@code true} numa instância nova (ainda não persistida nem
     * carregada). {@link #marcarPersistido()} zera assim que a linha entra/sai do banco.
     */
    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean novoRegistro = true;

    @Override
    public Pk getId() {
        return new Pk(uuid, versao);
    }

    @Override
    public boolean isNew() {
        return novoRegistro;
    }

    @PrePersist
    @PostLoad
    void marcarPersistido() {
        this.novoRegistro = false;
    }
}
