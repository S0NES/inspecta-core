package br.com.vistoria.core.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TokenHasher")
class TokenHasherTest {

    @Test
    @DisplayName("newToken generates valid URL-safe base64 token")
    void newToken_generates_valid_token() {
        String token1 = TokenHasher.newToken();
        String token2 = TokenHasher.newToken();

        assertThat(token1).isNotNull().isNotEmpty();
        assertThat(token2).isNotNull().isNotEmpty();
        assertThat(token1).isNotEqualTo(token2); // Should be random

        // URL-safe base64 should not contain + / =
        assertThat(token1).doesNotContain("+", "/", "=");
        assertThat(token2).doesNotContain("+", "/", "=");
    }

    @Test
    @DisplayName("newToken generates 32-byte random data (43 chars in base64)")
    void newToken_correct_length() {
        String token = TokenHasher.newToken();
        // 32 bytes encoded in base64 URL-safe without padding = 43 chars
        assertThat(token).hasSize(43);
    }

    @Test
    @DisplayName("newPrefix generates valid mk_ prefixed token")
    void newPrefix_generates_valid_prefix() {
        String prefix1 = TokenHasher.newPrefix();
        String prefix2 = TokenHasher.newPrefix();

        assertThat(prefix1).isNotNull().startsWith("mk_");
        assertThat(prefix2).isNotNull().startsWith("mk_");
        assertThat(prefix1).isNotEqualTo(prefix2); // Should be random
    }

    @Test
    @DisplayName("newPrefix generates correct length (mk_ + 12 hex chars for 6 bytes)")
    void newPrefix_correct_length() {
        String prefix = TokenHasher.newPrefix();
        // mk_ + 12 hex chars (6 bytes * 2) = 15 chars total
        assertThat(prefix).hasSize(15);
    }

    @Test
    @DisplayName("newPrefix generates only hex characters after mk_ prefix")
    void newPrefix_contains_only_hex() {
        String prefix = TokenHasher.newPrefix();
        String hexPart = prefix.substring(3); // Remove "mk_"

        assertThat(hexPart).matches("[0-9a-f]{12}");
    }

    @Test
    @DisplayName("sha256 returns consistent hash for same input")
    void sha256_consistent() {
        String input = "test-data-123";
        String hash1 = TokenHasher.sha256(input);
        String hash2 = TokenHasher.sha256(input);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("sha256 returns different hash for different inputs")
    void sha256_different_for_different_inputs() {
        String hash1 = TokenHasher.sha256("test1");
        String hash2 = TokenHasher.sha256("test2");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("sha256 returns 64-character hex string (256 bits)")
    void sha256_correct_length() {
        String hash = TokenHasher.sha256("any input");

        assertThat(hash).hasSize(64); // SHA-256 = 256 bits = 32 bytes = 64 hex chars
        assertThat(hash).matches("[0-9a-f]{64}"); // Only hex characters
    }

    @Test
    @DisplayName("sha256 handles empty string")
    void sha256_empty_string() {
        String hash = TokenHasher.sha256("");

        assertThat(hash).isNotNull().hasSize(64);
        // Empty string should produce a known SHA-256 hash
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("sha256 handles unicode characters")
    void sha256_unicode() {
        String input = "João Silva 漢字 🚀";
        String hash = TokenHasher.sha256(input);

        assertThat(hash).isNotNull().hasSize(64);
    }

    @Test
    @DisplayName("sha256 with null input throws exception")
    void sha256_null_input() {
        assertThatThrownBy(() -> TokenHasher.sha256(null))
                .isInstanceOf(Exception.class); // Can be NullPointerException or IllegalStateException wrapping it
    }

    @Test
    @DisplayName("newToken and newPrefix use different randomness (not identical)")
    void token_and_prefix_different_format() {
        String token = TokenHasher.newToken();
        String prefix = TokenHasher.newPrefix();

        // Token: URL-safe base64 (may contain - or _)
        assertThat(token).doesNotStartWith("mk_");
        assertThat(token).doesNotContain("+", "/", "="); // URL-safe base64 doesn't use these

        // Prefix: hex, mk_ prefix
        assertThat(prefix).startsWith("mk_");
        assertThat(prefix).matches("mk_[0-9a-f]{12}");
    }
}
