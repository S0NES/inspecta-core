package br.com.vistoria.core.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RealHttpGateway")
class RealHttpGatewayTest {

    private RealHttpGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new RealHttpGateway();
    }

    @Test
    @DisplayName("execute with GET request factory method")
    void execute_getRequest_factoryMethod() {
        URI uri = URI.create("http://api.example.com/users");
        Map<String, String> headers = Map.of("Accept", "application/json");

        HttpGatewayRequest request = HttpGatewayRequest.get(uri, headers);

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri()).isEqualTo(uri);
        assertThat(request.headers()).containsEntry("Accept", "application/json");
        assertThat(request.body()).isNull();
        assertThat(request.binario()).isFalse();
        assertThat(request.timeout()).isNull();
    }

    @Test
    @DisplayName("execute with POST request factory method")
    void execute_postRequest_factoryMethod() {
        URI uri = URI.create("http://api.example.com/users");
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        String body = "{\"name\": \"John\"}";

        HttpGatewayRequest request = HttpGatewayRequest.post(uri, headers, body);

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri()).isEqualTo(uri);
        assertThat(request.headers()).containsEntry("Content-Type", "application/json");
        assertThat(request.body()).isEqualTo(body);
        assertThat(request.binario()).isFalse();
    }

    @Test
    @DisplayName("execute with custom timeout")
    void execute_customTimeout() {
        URI uri = URI.create("http://api.example.com/test");
        Duration timeout = Duration.ofSeconds(60);

        HttpGatewayRequest request = HttpGatewayRequest.get(uri, Map.of())
                .comTimeout(timeout);

        assertThat(request.timeout()).isEqualTo(timeout);
    }

    @Test
    @DisplayName("execute canonical constructor")
    void execute_canonicalConstructor() {
        URI uri = URI.create("http://api.example.com/endpoint");
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        String body = "request body";

        HttpGatewayRequest request = new HttpGatewayRequest("PUT", uri, headers, body, false, Duration.ofSeconds(30));

        assertThat(request.method()).isEqualTo("PUT");
        assertThat(request.uri()).isEqualTo(uri);
        assertThat(request.headers()).containsEntry("Authorization", "Bearer token");
        assertThat(request.body()).isEqualTo(body);
        assertThat(request.binario()).isFalse();
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("execute binary GET factory method")
    void execute_getBinario() {
        URI uri = URI.create("http://api.example.com/file/download");
        Map<String, String> headers = Map.of();

        HttpGatewayRequest request = HttpGatewayRequest.getBinario(uri, headers);

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.binario()).isTrue();
        assertThat(request.body()).isNull();
    }

    @Test
    @DisplayName("execute binary POST factory method")
    void execute_postBinario() {
        URI uri = URI.create("http://api.example.com/file/upload");
        Map<String, String> headers = Map.of("Content-Type", "application/octet-stream");
        byte[] fileContent = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}; // JPEG header

        HttpGatewayRequest request = HttpGatewayRequest.postBinario(uri, headers, fileContent);

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.binario()).isTrue();
        assertThat(request.body()).isNotNull();
    }

    @Test
    @DisplayName("execute with empty headers defaults to empty map")
    void execute_emptyHeadersDefaultsToMap() {
        URI uri = URI.create("http://api.example.com/test");

        HttpGatewayRequest request = new HttpGatewayRequest("GET", uri, null, null, false, null);

        assertThat(request.headers()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("execute with provided headers is preserved")
    void execute_providedHeadersPreserved() {
        URI uri = URI.create("http://api.example.com/test");
        Map<String, String> headers = Map.of("X-Custom", "value", "Authorization", "Bearer token");

        HttpGatewayRequest request = new HttpGatewayRequest("GET", uri, headers, null, false, null);

        assertThat(request.headers()).hasSize(2).containsAllEntriesOf(headers);
    }

    @Test
    @DisplayName("execute with HTTPS URI")
    void execute_httpsUri() {
        URI uri = URI.create("https://secure.api.example.com/endpoint");

        HttpGatewayRequest request = HttpGatewayRequest.get(uri, Map.of());

        assertThat(request.uri().getScheme()).isEqualTo("https");
        assertThat(request.uri().getHost()).isEqualTo("secure.api.example.com");
    }

    @Test
    @DisplayName("execute with query parameters in URI")
    void execute_withQueryParameters() {
        URI uri = URI.create("http://api.example.com/search?q=test&limit=10");

        HttpGatewayRequest request = HttpGatewayRequest.get(uri, Map.of());

        assertThat(request.uri().getQuery()).isEqualTo("q=test&limit=10");
    }

    @Test
    @DisplayName("execute with path in URI")
    void execute_withPath() {
        URI uri = URI.create("http://api.example.com/v1/users/123");

        HttpGatewayRequest request = HttpGatewayRequest.get(uri, Map.of());

        assertThat(request.uri().getPath()).isEqualTo("/v1/users/123");
    }

    @Test
    @DisplayName("execute DELETE method")
    void execute_deleteMethod() {
        URI uri = URI.create("http://api.example.com/resource/123");

        HttpGatewayRequest request = new HttpGatewayRequest("DELETE", uri, Map.of(), null, false, null);

        assertThat(request.method()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("execute PATCH method")
    void execute_patchMethod() {
        URI uri = URI.create("http://api.example.com/resource/123");
        String body = "{\"status\": \"updated\"}";

        HttpGatewayRequest request = new HttpGatewayRequest("PATCH", uri, Map.of(), body, false, null);

        assertThat(request.method()).isEqualTo("PATCH");
        assertThat(request.body()).isEqualTo(body);
    }

    @Test
    @DisplayName("execute with unicode content in body")
    void execute_unicodeInBody() {
        URI uri = URI.create("http://api.example.com/text");
        String body = "{\"name\": \"João Silva 漢字 🚀\"}";

        HttpGatewayRequest request = HttpGatewayRequest.post(uri, Map.of(), body);

        assertThat(request.body()).contains("João").contains("漢字").contains("🚀");
    }

    @Test
    @DisplayName("execute comTimeout preserves other fields")
    void execute_comTimeout_preservesOtherFields() {
        URI uri = URI.create("http://api.example.com/test");
        Map<String, String> headers = Map.of("Auth", "token");
        String body = "test body";

        HttpGatewayRequest original = new HttpGatewayRequest("POST", uri, headers, body, true, null);
        HttpGatewayRequest withTimeout = original.comTimeout(Duration.ofSeconds(45));

        assertThat(withTimeout.method()).isEqualTo("POST");
        assertThat(withTimeout.uri()).isEqualTo(uri);
        assertThat(withTimeout.headers()).isEqualTo(headers);
        assertThat(withTimeout.body()).isEqualTo(body);
        assertThat(withTimeout.binario()).isTrue();
        assertThat(withTimeout.timeout()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    @DisplayName("execute timeout duration variations")
    void execute_timeoutVariations() {
        URI uri = URI.create("http://api.example.com/test");

        HttpGatewayRequest shortTimeout = HttpGatewayRequest.get(uri, Map.of())
                .comTimeout(Duration.ofSeconds(5));
        HttpGatewayRequest longTimeout = HttpGatewayRequest.get(uri, Map.of())
                .comTimeout(Duration.ofSeconds(300));

        assertThat(shortTimeout.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(longTimeout.timeout()).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    @DisplayName("response construction")
    void response_construction() {
        HttpGatewayResponse response = new HttpGatewayResponse(200,
                Map.of("Content-Type", "application/json"),
                "{\"result\": \"ok\"}");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers()).containsEntry("Content-Type", "application/json");
        assertThat(response.body()).isEqualTo("{\"result\": \"ok\"}");
    }

    @Test
    @DisplayName("response with error status code")
    void response_errorStatusCode() {
        HttpGatewayResponse response = new HttpGatewayResponse(404,
                Map.of(),
                "Not found");

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body()).isEqualTo("Not found");
    }

    @Test
    @DisplayName("response with server error")
    void response_serverError() {
        HttpGatewayResponse response = new HttpGatewayResponse(500,
                Map.of("Content-Type", "text/plain"),
                "Internal server error");

        assertThat(response.status()).isEqualTo(500);
    }

    @Test
    @DisplayName("response with multiple headers")
    void response_multipleHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Request-Id", "req-123");
        headers.put("Cache-Control", "no-cache");

        HttpGatewayResponse response = new HttpGatewayResponse(200, headers, "{}");

        assertThat(response.headers()).hasSize(3)
                .containsEntry("Content-Type", "application/json")
                .containsEntry("X-Request-Id", "req-123")
                .containsEntry("Cache-Control", "no-cache");
    }

    @Test
    @DisplayName("request get factory with null headers creates empty map")
    void request_getFactory_nullHeaders() {
        URI uri = URI.create("http://api.example.com/test");

        HttpGatewayRequest request = HttpGatewayRequest.get(uri, null);

        assertThat(request.headers()).isEmpty();
    }

    @Test
    @DisplayName("request post factory with empty body")
    void request_postFactory_emptyBody() {
        URI uri = URI.create("http://api.example.com/test");

        HttpGatewayRequest request = HttpGatewayRequest.post(uri, Map.of(), "");

        assertThat(request.body()).isEmpty();
    }
}
