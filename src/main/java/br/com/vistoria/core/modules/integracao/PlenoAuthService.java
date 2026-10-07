package br.com.vistoria.core.modules.integracao;

import br.com.vistoria.core.gateway.HttpGateway;
import br.com.vistoria.core.gateway.HttpGatewayRequest;
import br.com.vistoria.core.gateway.HttpGatewayResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class PlenoAuthService {

    private final PlenoTokenStore tokenStore;
    private final HttpGateway httpGateway;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${inspecta.pleno.base-url:}") private String baseUrl;
    @Value("${inspecta.pleno.client-id:}") private String clientId;
    @Value("${inspecta.pleno.client-secret:}") private String clientSecret;
    @Value("${inspecta.pleno.token-ttl-seconds:240}") private long tokenTtlSeconds;

    public PlenoAuthService(PlenoTokenStore tokenStore, HttpGateway httpGateway) {
        this.tokenStore = tokenStore;
        this.httpGateway = httpGateway;
    }

    public void renovar() {
        if (baseUrl.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
            log.debug("[pleno-auth] base-url/client-id/client-secret nao configurados — pulando renovacao");
            return;
        }
        try {
            String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            HttpGatewayResponse res = httpGateway.execute(HttpGatewayRequest.post(
                    URI.create(baseUrl + "/api/portalDoCliente/auth"),
                    Map.of("Authorization", "Basic " + basic), null));
            if (!res.sucesso()) {
                log.warn("[pleno-auth] HTTP {} ao autenticar", res.status());
                return;
            }
            JsonNode body = mapper.readTree(res.body().isBlank() ? "{}" : res.body());
            if (!body.path("success").asBoolean(false)) {
                log.warn("[pleno-auth] auth rejeitada: {}", body.path("error").path("message").asText());
                return;
            }
            String accessToken = body.path("data").path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                log.warn("[pleno-auth] resposta sem access_token: {}", res.body());
                return;
            }
            tokenStore.put(accessToken, tokenTtlSeconds);
            log.debug("[pleno-auth] token renovado, ttl~{}s", tokenTtlSeconds);
        } catch (Exception e) {
            log.warn("[pleno-auth] falha ao renovar token Pleno: {}", e.toString());
        }
    }
}
