package br.com.vistoria.core.gateway;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Texto (JSON, a maioria dos providers) trafega em UTF-8, igual sempre foi. Quando a request
 * e binario() (upload/download de arquivo — ver HttpGatewayRequest), troca pra ISO-8859-1
 * (mapeamento 1:1 byte<->char) nos dois sentidos, senao o HttpClient reencodaria os bytes
 * assumindo texto e corromperia o arquivo.
 */
@Component
@Profile("prod")
public class RealHttpGateway implements HttpGateway {

    private static final Duration TIMEOUT_PADRAO = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public HttpGatewayResponse execute(HttpGatewayRequest request) throws Exception {
        Charset charset = request.binario() ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8;

        HttpRequest.BodyPublisher publisher = request.body() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(request.body().getBytes(charset));

        Duration timeout = request.timeout() != null ? request.timeout() : TIMEOUT_PADRAO;
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(timeout)
                .method(request.method(), publisher);
        request.headers().forEach(builder::header);

        HttpResponse<byte[]> res = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

        Map<String, String> headers = res.headers().map().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(",", e.getValue()),
                        (a, b) -> a, LinkedHashMap::new));

        return new HttpGatewayResponse(res.statusCode(), headers, new String(res.body(), charset));
    }
}
