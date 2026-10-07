package br.com.vistoria.core.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MockHttpGateway")
class MockHttpGatewayTest {

    private MockHttpGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MockHttpGateway();
    }

    @Test
    @DisplayName("execute returns default 200 OK when queue empty")
    void execute_emptyQueue_returnsDefault() throws InterruptedException {
        HttpGatewayRequest request = HttpGatewayRequest.get(URI.create("http://example.com"), null);

        HttpGatewayResponse response = gateway.execute(request);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
    }

    @Test
    @DisplayName("execute returns queued response")
    void execute_queuedResponse_returns() throws InterruptedException {
        gateway.programarProxima(201, "Success");
        HttpGatewayRequest request = HttpGatewayRequest.post(URI.create("http://example.com/create"), null, null);

        HttpGatewayResponse response = gateway.execute(request);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body()).isEqualTo("Success");
    }

    @Test
    @DisplayName("execute with delayed response ready returns immediately")
    void execute_delayedResponseReady() throws InterruptedException {
        // Schedule a response with 100ms delay
        gateway.programarProximaComAtraso(Duration.ofMillis(100), 202, "Accepted");

        // Wait for it to be ready
        Thread.sleep(150);

        HttpGatewayRequest request = HttpGatewayRequest.get(URI.create("http://example.com"), null);
        HttpGatewayResponse response = gateway.execute(request);

        assertThat(response.status()).isEqualTo(202);
        assertThat(response.body()).isEqualTo("Accepted");
    }

    @Test
    @DisplayName("execute waits for delayed response")
    void execute_delayedResponse_waits() throws InterruptedException {
        Duration delay = Duration.ofMillis(200);
        gateway.programarProximaComAtraso(delay, 200, "OK");

        HttpGatewayRequest request = HttpGatewayRequest.get(URI.create("http://example.com"), null);
        long start = System.currentTimeMillis();

        HttpGatewayResponse response = gateway.execute(request);

        long elapsed = System.currentTimeMillis() - start;
        assertThat(response.status()).isEqualTo(200);
        assertThat(elapsed).isGreaterThanOrEqualTo(delay.toMillis() - 10);
    }

    @Test
    @DisplayName("execute throws on timeout when delayed response exceeds timeout")
    void execute_timeout_throws() {
        // Schedule response with 500ms delay
        gateway.programarProximaComAtraso(Duration.ofMillis(500), 200, "Too slow");

        // Request with 100ms timeout
        HttpGatewayRequest request = new HttpGatewayRequest(
                "GET", URI.create("http://example.com"), null, null, false, Duration.ofMillis(100));

        assertThatThrownBy(() -> gateway.execute(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    @DisplayName("execute with timeout null waits indefinitely for delayed response")
    void execute_noTimeout_waits() throws InterruptedException {
        gateway.programarProximaComAtraso(Duration.ofMillis(100), 200, "OK");

        HttpGatewayRequest request = HttpGatewayRequest.get(URI.create("http://example.com"), null);

        HttpGatewayResponse response = gateway.execute(request);

        assertThat(response.status()).isEqualTo(200);
    }

    @Test
    @DisplayName("limpar clears all queued responses")
    void limpar_clearsQueues() throws InterruptedException {
        gateway.programarProxima(201, "First");
        gateway.programarProximaComAtraso(Duration.ofMillis(100), 202, "Second");

        gateway.limpar();

        HttpGatewayRequest request = HttpGatewayRequest.get(URI.create("http://example.com"), null);
        HttpGatewayResponse response = gateway.execute(request);

        // Should get default response
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
    }

    @Test
    @DisplayName("execute consumes responses from queue in order")
    void execute_fifoOrder() throws InterruptedException {
        gateway.programarProxima(201, "First");
        gateway.programarProxima(202, "Second");
        gateway.programarProxima(203, "Third");

        HttpGatewayRequest request = HttpGatewayRequest.get(URI.create("http://example.com"), null);

        HttpGatewayResponse r1 = gateway.execute(request);
        HttpGatewayResponse r2 = gateway.execute(request);
        HttpGatewayResponse r3 = gateway.execute(request);

        assertThat(r1.status()).isEqualTo(201);
        assertThat(r2.status()).isEqualTo(202);
        assertThat(r3.status()).isEqualTo(203);
    }

    @Test
    @DisplayName("multiple delayed responses queue correctly")
    void execute_multipleDelayedResponses() throws InterruptedException {
        gateway.programarProximaComAtraso(Duration.ofMillis(50), 201, "First");
        gateway.programarProximaComAtraso(Duration.ofMillis(100), 202, "Second");

        HttpGatewayRequest request = new HttpGatewayRequest(
                "GET", URI.create("http://example.com"), null, null, false, Duration.ofSeconds(5));

        Thread.sleep(150);
        HttpGatewayResponse r1 = gateway.execute(request);
        HttpGatewayResponse r2 = gateway.execute(request);

        assertThat(r1.status()).isEqualTo(201);
        assertThat(r2.status()).isEqualTo(202);
    }
}
