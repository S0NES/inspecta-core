package br.com.vistoria.core.modules.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlenoTokenStore")
class PlenoTokenStoreTest {

    @Mock
    private IntegracaoTokenCacheRepository cache;

    private PlenoTokenStore store;

    @BeforeEach
    void setUp() {
        store = new PlenoTokenStore(cache);
    }

    @Test
    @DisplayName("get returns token when valid and not expired")
    void get_returnsValidToken() {
        IntegracaoTokenCache cached = new IntegracaoTokenCache();
        cached.setChave("pleno:access_token");
        cached.setValor("token123");
        cached.setExpiraEm(Instant.now().plusSeconds(3600));

        when(cache.findById("pleno:access_token")).thenReturn(Optional.of(cached));

        String result = store.get();

        assertThat(result).isEqualTo("token123");
        verify(cache, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("get returns null when not found")
    void get_returnsNullWhenNotFound() {
        when(cache.findById("pleno:access_token")).thenReturn(Optional.empty());

        String result = store.get();

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("get returns null and deletes when expired")
    void get_returnsNullAndDeletesExpired() {
        IntegracaoTokenCache cached = new IntegracaoTokenCache();
        cached.setChave("pleno:access_token");
        cached.setValor("token123");
        cached.setExpiraEm(Instant.now().minusSeconds(60));

        when(cache.findById("pleno:access_token")).thenReturn(Optional.of(cached));

        String result = store.get();

        assertThat(result).isNull();
        verify(cache).deleteById("pleno:access_token");
    }

    @Test
    @DisplayName("put stores token with calculated TTL")
    void put_storesToken() {
        store.put("newtoken456", 300);

        verify(cache).save(any(IntegracaoTokenCache.class));
    }

    @Test
    @DisplayName("put applies 60 second safety buffer")
    void put_appliesSafetyBuffer() {
        IntegracaoTokenCache[] savedEntity = new IntegracaoTokenCache[1];

        doAnswer(invocation -> {
            savedEntity[0] = invocation.getArgument(0);
            return null;
        }).when(cache).save(any(IntegracaoTokenCache.class));

        Instant beforePut = Instant.now();
        store.put("token", 300);
        Instant afterPut = Instant.now();

        IntegracaoTokenCache saved = savedEntity[0];
        assertThat(saved.getValor()).isEqualTo("token");
        assertThat(saved.getChave()).isEqualTo("pleno:access_token");

        // TTL = max(300 - 60, 30) = 240 seconds
        Instant expectedExpiry = Instant.now().plusSeconds(240);
        assertThat(saved.getExpiraEm()).isBetween(
            expectedExpiry.minusSeconds(2),
            expectedExpiry.plusSeconds(2)
        );
    }

    @Test
    @DisplayName("put minimum TTL is 30 seconds")
    void put_minimumTtl() {
        IntegracaoTokenCache[] savedEntity = new IntegracaoTokenCache[1];

        doAnswer(invocation -> {
            savedEntity[0] = invocation.getArgument(0);
            return null;
        }).when(cache).save(any(IntegracaoTokenCache.class));

        store.put("token", 60);

        IntegracaoTokenCache saved = savedEntity[0];
        // TTL = max(60 - 60, 30) = 30 seconds
        Instant expectedExpiry = Instant.now().plusSeconds(30);
        assertThat(saved.getExpiraEm()).isBetween(
            expectedExpiry.minusSeconds(2),
            expectedExpiry.plusSeconds(2)
        );
    }

    @Test
    @DisplayName("get and put lifecycle works correctly")
    void lifecycle_getAndPut() {
        // First put a token
        IntegracaoTokenCache[] savedEntity = new IntegracaoTokenCache[1];
        doAnswer(invocation -> {
            savedEntity[0] = invocation.getArgument(0);
            return null;
        }).when(cache).save(any(IntegracaoTokenCache.class));

        store.put("token1", 300);

        // Then get it
        when(cache.findById("pleno:access_token")).thenReturn(Optional.of(savedEntity[0]));

        String result = store.get();

        assertThat(result).isEqualTo("token1");
    }

    @Test
    @DisplayName("get returns null once expiraEm is in the past")
    void get_checksExpirationBoundary() {
        IntegracaoTokenCache cached = new IntegracaoTokenCache();
        cached.setChave("pleno:access_token");
        cached.setValor("token");
        cached.setExpiraEm(Instant.now().minusSeconds(1));

        when(cache.findById("pleno:access_token")).thenReturn(Optional.of(cached));

        String result = store.get();

        assertThat(result).isNull();
    }
}
