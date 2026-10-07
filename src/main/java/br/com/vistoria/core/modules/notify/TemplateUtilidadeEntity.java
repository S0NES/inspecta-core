package br.com.vistoria.core.modules.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Ligação editável utilidade → template de e-mail ({@code notify.template_utilidades}).
 *
 * <p>PK em {@code utilidade} garante <b>um</b> template por utilidade. {@code templateId} aponta
 * para o {@code uuid} lógico de {@code notify.message_templates} (a resolução pega a maior versão
 * ativa). Semeada em {@code V33} com o UUID padrão de cada {@link TemplateUtilidade}.</p>
 */
@Entity
@Table(name = "template_utilidades", schema = "notify")
@Getter
@Setter
@NoArgsConstructor
public class TemplateUtilidadeEntity {

    @Id
    private String utilidade;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "atualizado_por")
    private UUID atualizadoPor;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm = Instant.now();
}
