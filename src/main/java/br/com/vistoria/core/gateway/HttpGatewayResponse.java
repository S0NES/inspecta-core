package br.com.vistoria.core.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public record HttpGatewayResponse(int status, Map<String, String> headers, String body) {

    public HttpGatewayResponse {
        headers = headers == null ? Map.of() : headers;
        body = body == null ? "" : body;
    }

    public HttpGatewayResponse(int status, String body) {
        this(status, Map.of(), body);
    }

    public boolean sucesso() { return status / 100 == 2; }

    /** Recupera os bytes originais quando a request foi feita com HttpGatewayRequest.*Binario. */
    public byte[] bodyComoBytes() {
        return body.getBytes(StandardCharsets.ISO_8859_1);
    }
}
