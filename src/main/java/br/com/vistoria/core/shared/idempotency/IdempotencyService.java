package br.com.vistoria.core.shared.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Idempotencia HTTP para as rotas mutantes /v1/* do inspecta-api.
 *
 * Fluxo do {@code IdempotencyFilter}:
 * <pre>
 *   lookup(chave, hash):
 *     HIT                -> replica statusHttp + responseBody, nao reexecuta
 *     CONFLICT           -> 422 (mesma chave, corpo diferente)
 *     CLAIMED_IN_PROGRESS-> 409 (requisicao gemea ainda processando)
 *     MISS               -> reivindicar() -> processa -> concluir() (2xx) | liberar() (erro)
 * </pre>
 * Estado no Postgres (regra 7 — inspecta-api roda replicas=2, nada em memoria).
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRepository repo;

    public enum Match { MISS, CLAIMED_IN_PROGRESS, HIT, CONFLICT }

    public record Lookup(Match match, Integer statusHttp, String responseBody) {
        static Lookup miss() { return new Lookup(Match.MISS, null, null); }
    }

    @Transactional(readOnly = true)
    public Lookup lookup(String chave, String requestHash) {
        return repo.findById(chave).map(k -> {
            if (!k.getRequestHash().equals(requestHash)) return new Lookup(Match.CONFLICT, null, null);
            if (k.getStatusHttp() == null) return new Lookup(Match.CLAIMED_IN_PROGRESS, null, null);
            return new Lookup(Match.HIT, k.getStatusHttp(), k.getResponseBody());
        }).orElseGet(Lookup::miss);
    }

    /**
     * Reivindica a chave via INSERT (status nulo = "em processamento"). Roda em TX
     * propria para commitar antes do processamento da requisicao — assim a gemea
     * concorrente ve a linha e recebe 409. {@code true} = reivindicada aqui;
     * {@code false} = ja existe (o chamador deve re-{@link #lookup}).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reivindicar(String chave, String metodo, String path, String requestHash,
                               UUID tenantId, Duration ttl) {
        if (repo.existsById(chave)) return false;
        IdempotencyKey k = new IdempotencyKey();
        k.setChave(chave);
        k.setMetodo(metodo);
        k.setPath(path);
        k.setRequestHash(requestHash);
        k.setTenantId(tenantId);
        k.setExpiraEm(Instant.now().plus(ttl));
        try {
            repo.saveAndFlush(k);
            return true;
        } catch (DataIntegrityViolationException corrida) {
            return false;
        }
    }

    @Transactional
    public void concluir(String chave, int statusHttp, String responseBody) {
        repo.findById(chave).ifPresent(k -> {
            k.setStatusHttp(statusHttp);
            k.setResponseBody(responseBody == null || responseBody.isEmpty() ? null : responseBody);
        });
    }

    /** Requisicao original terminou fora de 2xx: libera a chave para nova tentativa. */
    @Transactional
    public void liberar(String chave) {
        repo.deleteById(chave);
    }

    @Transactional
    public int purgarExpirados() {
        return repo.deleteByExpiraEmBefore(Instant.now());
    }

    /**
     * Hash estavel de metodo + path + tenant + corpo. O tenant no hash torna
     * colisao cross-tenant impossivel mesmo com a mesma chave escolhida pelo cliente.
     */
    public static String requestHash(String metodo, String path, UUID tenantId, byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(metodo.getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            md.update(path.getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            md.update(String.valueOf(tenantId).getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            if (body != null) md.update(body);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
