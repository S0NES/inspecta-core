package br.com.vistoria.core.gateway;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * body() trafega texto (JSON) em UTF-8 por padrao — igual sempre foi. Quando binario=true
 * (upload/download de arquivo, ex.: PDF na assinatura/Autentique), o transporte usa ISO-8859-1 no lugar
 * (mapeamento 1:1 byte<->char, sem reencode com perda); use os factories *Binario e
 * HttpGatewayResponse.bodyComoBytes() pra ir e voltar sem corromper.
 *
 * timeout() e opcional (null por padrao): quando ausente, RealHttpGateway aplica seu timeout
 * default (30s), pensado pra integracoes assincronas de background. Callers que rodam em thread
 * de request do usuario (ex.: leitura on-demand da Pleno em inspecta-api) e precisam de um limite mais
 * curto devem usar comTimeout(...) pra sobrescrever por chamada, sem afetar os demais callers.
 */
public record HttpGatewayRequest(String method, URI uri, Map<String, String> headers, String body, boolean binario,
                                  Duration timeout) {

    public HttpGatewayRequest {
        headers = headers == null ? Map.of() : headers;
    }

    public static HttpGatewayRequest get(URI uri, Map<String, String> headers) {
        return new HttpGatewayRequest("GET", uri, headers, null, false, null);
    }

    public static HttpGatewayRequest post(URI uri, Map<String, String> headers, String body) {
        return new HttpGatewayRequest("POST", uri, headers, body, false, null);
    }

    public static HttpGatewayRequest getBinario(URI uri, Map<String, String> headers) {
        return new HttpGatewayRequest("GET", uri, headers, null, true, null);
    }

    public static HttpGatewayRequest postBinario(URI uri, Map<String, String> headers, byte[] corpo) {
        return new HttpGatewayRequest("POST", uri, headers, new String(corpo, StandardCharsets.ISO_8859_1), true, null);
    }

    /** Retorna uma copia desta request com um timeout especifico, sobrescrevendo o default do gateway. */
    public HttpGatewayRequest comTimeout(Duration timeout) {
        return new HttpGatewayRequest(method, uri, headers, body, binario, timeout);
    }
}
