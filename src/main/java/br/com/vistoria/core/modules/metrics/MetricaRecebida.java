package br.com.vistoria.core.modules.metrics;

import java.time.Instant;

/**
 * Resultado canonico da interpretacao de um payload de webhook de provedor.
 *
 * @param eventId    chave de deduplicacao (unica por evento do provedor)
 * @param externalId id da mensagem no provedor (casa com {@code metrics.envios.external_id})
 * @param status     status canonico
 * @param statusRaw  string de status crua do provedor (forense)
 * @param ocorridoEm quando o evento ocorreu no provedor (pode ser nulo)
 */
public record MetricaRecebida(String eventId, String externalId, StatusEnvio status,
                              String statusRaw, Instant ocorridoEm) {}
