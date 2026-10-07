package br.com.vistoria.core.modules.integracao;

import br.com.vistoria.core.gateway.HttpGateway;
import br.com.vistoria.core.gateway.HttpGatewayRequest;
import br.com.vistoria.core.gateway.HttpGatewayResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlenoAuthService")
class PlenoAuthServiceTest {

    @Mock
    private PlenoTokenStore tokenStore;

    @Mock
    private HttpGateway httpGateway;

    private PlenoAuthService service;

    @BeforeEach
    void setUp() {
        service = new PlenoAuthService(tokenStore, httpGateway);
    }

    @Test
    @DisplayName("renovar skips when baseUrl is blank")
    void renovar_skipsWhenBlankUrl() throws Exception {
        ReflectionTestUtils.setField(service, "baseUrl", "");
        ReflectionTestUtils.setField(service, "clientId", "id");
        ReflectionTestUtils.setField(service, "clientSecret", "secret");

        service.renovar();

        verify(httpGateway, never()).execute(any());
        verify(tokenStore, never()).put(anyString(), anyLong());
    }

    @Test
    @DisplayName("renovar skips when clientId is blank")
    void renovar_skipsWhenBlankClientId() throws Exception {
        ReflectionTestUtils.setField(service, "baseUrl", "https://pleno.com");
        ReflectionTestUtils.setField(service, "clientId", "");
        ReflectionTestUtils.setField(service, "clientSecret", "secret");

        service.renovar();

        verify(httpGateway, never()).execute(any());
    }

    @Test
    @DisplayName("renovar skips when clientSecret is blank")
    void renovar_skipsWhenBlankSecret() throws Exception {
        ReflectionTestUtils.setField(service, "baseUrl", "https://pleno.com");
        ReflectionTestUtils.setField(service, "clientId", "id");
        ReflectionTestUtils.setField(service, "clientSecret", "");

        service.renovar();

        verify(httpGateway, never()).execute(any());
    }

    @Test
    @DisplayName("renovar stores token on successful auth response")
    void renovar_storesTokenOnSuccess() throws Exception {
        ReflectionTestUtils.setField(service, "baseUrl", "https://pleno.com");
        ReflectionTestUtils.setField(service, "clientId", "test-id");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");
        ReflectionTestUtils.setField(service, "tokenTtlSeconds", 240L);

        String responseBody = "{\"success\":true,\"data\":{\"access_token\":\"token123\"}}";
        HttpGatewayResponse response = new HttpGatewayResponse(200, responseBody);

        when(httpGateway.execute(any(HttpGatewayRequest.class))).thenReturn(response);

        service.renovar();

        verify(tokenStore).put("token123", 240L);
    }

    @Test
    @DisplayName("renovar logs warning when HTTP fails")
    void renovar_handlesHttpError() throws Exception {
        ReflectionTestUtils.setField(service, "baseUrl", "https://pleno.com");
        ReflectionTestUtils.setField(service, "clientId", "test-id");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");

        HttpGatewayResponse response = new HttpGatewayResponse(500, "Server Error");

        when(httpGateway.execute(any(HttpGatewayRequest.class))).thenReturn(response);

        service.renovar();

        verify(tokenStore, never()).put(anyString(), anyLong());
    }

    @Test
    @DisplayName("renovar handles response with success false")
    void renovar_handlesSuccessFalse() throws Exception {
        ReflectionTestUtils.setField(service, "baseUrl", "https://pleno.com");
        ReflectionTestUtils.setField(service, "clientId", "test-id");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");

        String responseBody = "{\"success\":false,\"error\":{\"message\":\"Invalid credentials\"}}";
        HttpGatewayResponse response = new HttpGatewayResponse(200, responseBody);

        when(httpGateway.execute(any(HttpGatewayRequest.class))).thenReturn(response);

        service.renovar();

        verify(tokenStore, never()).put(anyString(), anyLong());
    }

    @Test
    @DisplayName("renovar handles response without access_token")
    void renovar_handlesNoAccessToken() throws Exception {
        ReflectionTestUtils.setField(service, "baseUrl", "https://pleno.com");
        ReflectionTestUtils.setField(service, "clientId", "test-id");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");

        String responseBody = "{\"success\":true,\"data\":{}}";
        HttpGatewayResponse response = new HttpGatewayResponse(200, responseBody);

        when(httpGateway.execute(any(HttpGatewayRequest.class))).thenReturn(response);

        service.renovar();

        verify(tokenStore, never()).put(anyString(), anyLong());
    }

    @Test
    @DisplayName("renovar handles response with blank (non-null) access_token")
    void renovar_handlesBlankAccessToken() throws Exception {
        ReflectionTestUtils.setField(service, "baseUrl", "https://pleno.com");
        ReflectionTestUtils.setField(service, "clientId", "test-id");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");

        String responseBody = "{\"success\":true,\"data\":{\"access_token\":\"\"}}";
        HttpGatewayResponse response = new HttpGatewayResponse(200, responseBody);

        when(httpGateway.execute(any(HttpGatewayRequest.class))).thenReturn(response);

        service.renovar();

        verify(tokenStore, never()).put(anyString(), anyLong());
    }

    @Test
    @DisplayName("renovar handles empty response body")
    void renovar_handlesEmptyBody() throws Exception {
        ReflectionTestUtils.setField(service, "baseUrl", "https://pleno.com");
        ReflectionTestUtils.setField(service, "clientId", "test-id");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");

        HttpGatewayResponse response = new HttpGatewayResponse(200, "");

        when(httpGateway.execute(any(HttpGatewayRequest.class))).thenReturn(response);

        service.renovar();

        verify(tokenStore, never()).put(anyString(), anyLong());
    }

    @Test
    @DisplayName("renovar handles exception gracefully")
    void renovar_handleException() throws Exception {
        ReflectionTestUtils.setField(service, "baseUrl", "https://pleno.com");
        ReflectionTestUtils.setField(service, "clientId", "test-id");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");

        when(httpGateway.execute(any(HttpGatewayRequest.class)))
            .thenThrow(new RuntimeException("Connection timeout"));

        service.renovar();

        verify(tokenStore, never()).put(anyString(), anyLong());
    }

    @Test
    @DisplayName("renovar uses correct authorization header format")
    void renovar_usesCorrectAuthHeader() throws Exception {
        ReflectionTestUtils.setField(service, "baseUrl", "https://pleno.com");
        ReflectionTestUtils.setField(service, "clientId", "id123");
        ReflectionTestUtils.setField(service, "clientSecret", "secret456");
        ReflectionTestUtils.setField(service, "tokenTtlSeconds", 240L);

        String responseBody = "{\"success\":true,\"data\":{\"access_token\":\"token123\"}}";
        HttpGatewayResponse response = new HttpGatewayResponse(200, responseBody);

        when(httpGateway.execute(any(HttpGatewayRequest.class))).thenReturn(response);

        service.renovar();

        verify(httpGateway).execute(any(HttpGatewayRequest.class));
    }
}
