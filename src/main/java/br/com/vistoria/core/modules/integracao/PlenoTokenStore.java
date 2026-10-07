package br.com.vistoria.core.modules.integracao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class PlenoTokenStore {

    private static final String KEY = "pleno:access_token";
    private final IntegracaoTokenCacheRepository cache;

    public String get() {
        var stored = cache.findById(KEY).orElse(null);
        if (stored == null) {
            return null;
        }
        if (Instant.now().isAfter(stored.getExpiraEm())) {
            cache.deleteById(KEY);
            return null;
        }
        return stored.getValor();
    }

    public void put(String token, long expiresInSeconds) {
        long ttlSeconds = Math.max(expiresInSeconds - 60, 30);
        IntegracaoTokenCache cached = new IntegracaoTokenCache();
        cached.setChave(KEY);
        cached.setValor(token);
        cached.setExpiraEm(Instant.now().plusSeconds(ttlSeconds));
        cache.save(cached);
    }
}
