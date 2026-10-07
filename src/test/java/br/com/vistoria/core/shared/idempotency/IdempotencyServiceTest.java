package br.com.vistoria.core.shared.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService")
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRepository repo;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repo);
    }

    private IdempotencyKey row(String hash, Integer status, String body) {
        IdempotencyKey k = new IdempotencyKey();
        k.setChave("k1");
        k.setRequestHash(hash);
        k.setStatusHttp(status);
        k.setResponseBody(body);
        return k;
    }

    @Test
    @DisplayName("lookup: sem linha -> MISS")
    void lookup_miss() {
        when(repo.findById("k1")).thenReturn(Optional.empty());
        assertThat(service.lookup("k1", "h").match()).isEqualTo(IdempotencyService.Match.MISS);
    }

    @Test
    @DisplayName("lookup: hash igual + status preenchido -> HIT com corpo")
    void lookup_hit() {
        when(repo.findById("k1")).thenReturn(Optional.of(row("h", 201, "{\"id\":1}")));
        var l = service.lookup("k1", "h");
        assertThat(l.match()).isEqualTo(IdempotencyService.Match.HIT);
        assertThat(l.statusHttp()).isEqualTo(201);
        assertThat(l.responseBody()).isEqualTo("{\"id\":1}");
    }

    @Test
    @DisplayName("lookup: hash diferente -> CONFLICT")
    void lookup_conflict() {
        when(repo.findById("k1")).thenReturn(Optional.of(row("outro-hash", 201, "x")));
        assertThat(service.lookup("k1", "h").match()).isEqualTo(IdempotencyService.Match.CONFLICT);
    }

    @Test
    @DisplayName("lookup: hash igual mas status nulo -> CLAIMED_IN_PROGRESS")
    void lookup_inProgress() {
        when(repo.findById("k1")).thenReturn(Optional.of(row("h", null, null)));
        assertThat(service.lookup("k1", "h").match()).isEqualTo(IdempotencyService.Match.CLAIMED_IN_PROGRESS);
    }

    @Test
    @DisplayName("reivindicar: chave nova -> insere e retorna true")
    void reivindicar_nova() {
        when(repo.existsById("k1")).thenReturn(false);
        boolean ok = service.reivindicar("k1", "POST", "/v1/x", "h", UUID.fromString("00000000-0000-0000-0000-000000000007"), Duration.ofHours(24));
        assertThat(ok).isTrue();
        verify(repo).saveAndFlush(any(IdempotencyKey.class));
    }

    @Test
    @DisplayName("reivindicar: chave ja existe -> false, sem insert")
    void reivindicar_existente() {
        when(repo.existsById("k1")).thenReturn(true);
        boolean ok = service.reivindicar("k1", "POST", "/v1/x", "h", UUID.fromString("00000000-0000-0000-0000-000000000007"), Duration.ofHours(24));
        assertThat(ok).isFalse();
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("concluir: grava status e corpo; corpo vazio vira null")
    void concluir_gravaResultado() {
        IdempotencyKey k = row("h", null, null);
        when(repo.findById("k1")).thenReturn(Optional.of(k));

        service.concluir("k1", 201, "{\"id\":9}");
        assertThat(k.getStatusHttp()).isEqualTo(201);
        assertThat(k.getResponseBody()).isEqualTo("{\"id\":9}");

        service.concluir("k1", 204, "");
        assertThat(k.getResponseBody()).isNull();
    }

    @Test
    @DisplayName("requestHash: deterministico e sensivel ao tenant e ao corpo")
    void requestHash_deterministicoESensivel() {
        String a = IdempotencyService.requestHash("POST", "/v1/x", UUID.fromString("00000000-0000-0000-0000-000000000001"), "body".getBytes());
        String b = IdempotencyService.requestHash("POST", "/v1/x", UUID.fromString("00000000-0000-0000-0000-000000000001"), "body".getBytes());
        String outroTenant = IdempotencyService.requestHash("POST", "/v1/x", UUID.fromString("00000000-0000-0000-0000-000000000002"), "body".getBytes());
        String outroCorpo = IdempotencyService.requestHash("POST", "/v1/x", UUID.fromString("00000000-0000-0000-0000-000000000001"), "body2".getBytes());
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(outroTenant);
        assertThat(a).isNotEqualTo(outroCorpo);
    }
}
