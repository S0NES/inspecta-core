package br.com.vistoria.core.modules.metrics;

/**
 * Status canonico de uma mensagem de saida, normalizado a partir do vocabulario
 * de cada provedor pelo {@link WebhookInterpretador}. O superset cobre e-mail,
 * SMS, WhatsApp e push para evitar migration de drift de enum (ver V18); a string
 * crua do provedor fica em {@code metrics.envios.status_raw}.
 *
 * <p>A ordem dos valores define o avanco do ciclo de vida: um status so e aplicado
 * se {@link #isAfter(StatusEnvio)} em relacao ao atual (forward-only), exceto os
 * terminais de falha, que sempre vencem.</p>
 */
public enum StatusEnvio {
    CRIADO,
    ENVIADO,
    ENTREGUE,
    ABERTO,
    CLICADO,
    BOUNCE,
    RECLAMACAO,
    FALHA,
    DESCONHECIDO;

    /** Valor gravado no banco (bate com o CHECK de {@code metrics.envios.status}). */
    public String dbValue() {
        return name().toLowerCase();
    }

    public static StatusEnvio fromDb(String value) {
        if (value == null || value.isBlank()) return DESCONHECIDO;
        try {
            return StatusEnvio.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DESCONHECIDO;
        }
    }

    /**
     * Melhor esforco para mapear uma string arbitraria de provedor. Interpretadores
     * fazem o mapeamento preciso; este metodo e um fallback que nunca lanca.
     */
    public static StatusEnvio normalizar(String raw) {
        if (raw == null || raw.isBlank()) return DESCONHECIDO;
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "criado", "created", "queued", "accepted" -> CRIADO;
            case "enviado", "sent", "processed", "sending", "submitted", "delivery_delayed" -> ENVIADO;
            case "entregue", "delivered", "delivery", "success" -> ENTREGUE;
            case "aberto", "opened", "open" -> ABERTO;
            case "clicado", "clicked", "click" -> CLICADO;
            case "bounce", "bounced", "hard_bounce", "soft_bounce", "undelivered" -> BOUNCE;
            case "reclamacao", "complained", "complaint", "spam" -> RECLAMACAO;
            case "falha", "failed", "failure", "error", "rejected", "dropped" -> FALHA;
            default -> DESCONHECIDO;
        };
    }

    /** {@code true} se {@code this} representa um estagio posterior a {@code outro}. */
    public boolean isAfter(StatusEnvio outro) {
        if (outro == null) return true;
        // Falha/bounce/reclamacao sao terminais: uma vez marcados, nao regridem para "entregue".
        if (outro == FALHA || outro == BOUNCE || outro == RECLAMACAO) return false;
        return this.ordinal() > outro.ordinal();
    }
}
