package br.com.vistoria.core.modules.metrics;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Converte o JSON especifico de um provedor de webhook para o objeto canonico
 * {@link MetricaRecebida}. Uma implementacao por provedor; o segmento {@code {broker}}
 * da URL {@code /v1/webhooks/metricas/{broker}} seleciona qual usar.
 *
 * <p>Adicionar um provedor = criar um {@code @Component} que implementa esta interface.</p>
 */
public interface WebhookInterpretador {

    /** Nome do broker (bate com o segmento da URL, minusculo). */
    String broker();

    /**
     * @return a metrica canonica, ou {@code null} se o payload nao for um evento
     *         de entregabilidade relevante (o receiver ignora nulos).
     */
    MetricaRecebida interpretar(JsonNode payload);
}
