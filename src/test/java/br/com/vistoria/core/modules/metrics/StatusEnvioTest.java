package br.com.vistoria.core.modules.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StatusEnvio")
class StatusEnvioTest {

    @Test
    @DisplayName("dbValue is the lowercase name and round-trips via fromDb")
    void dbValueRoundTrip() {
        for (StatusEnvio s : StatusEnvio.values()) {
            assertThat(s.dbValue()).isEqualTo(s.name().toLowerCase());
            assertThat(StatusEnvio.fromDb(s.dbValue())).isEqualTo(s);
        }
    }

    @Test
    @DisplayName("fromDb tolerates null, blank and garbage")
    void fromDbFallbacks() {
        assertThat(StatusEnvio.fromDb(null)).isEqualTo(StatusEnvio.DESCONHECIDO);
        assertThat(StatusEnvio.fromDb("  ")).isEqualTo(StatusEnvio.DESCONHECIDO);
        assertThat(StatusEnvio.fromDb("nope")).isEqualTo(StatusEnvio.DESCONHECIDO);
        assertThat(StatusEnvio.fromDb(" ENTREGUE ")).isEqualTo(StatusEnvio.ENTREGUE);
    }

    @ParameterizedTest
    @CsvSource({
            "sent,ENVIADO", "submitted,ENVIADO", "delivery_delayed,ENVIADO",
            "delivered,ENTREGUE", "success,ENTREGUE",
            "opened,ABERTO", "clicked,CLICADO",
            "bounced,BOUNCE", "hard_bounce,BOUNCE", "undelivered,BOUNCE",
            "complained,RECLAMACAO", "spam,RECLAMACAO",
            "failed,FALHA", "rejected,FALHA", "dropped,FALHA",
            "queued,CRIADO",
            "totally-unknown,DESCONHECIDO"
    })
    @DisplayName("normalizar maps provider vocabulary to the canonical enum")
    void normalizar(String raw, StatusEnvio esperado) {
        assertThat(StatusEnvio.normalizar(raw)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("normalizar never throws on null/blank")
    void normalizarNullBlank() {
        assertThat(StatusEnvio.normalizar(null)).isEqualTo(StatusEnvio.DESCONHECIDO);
        assertThat(StatusEnvio.normalizar("")).isEqualTo(StatusEnvio.DESCONHECIDO);
    }

    @Test
    @DisplayName("isAfter advances forward-only and never regresses from a terminal state")
    void isAfter() {
        assertThat(StatusEnvio.ENTREGUE.isAfter(StatusEnvio.ENVIADO)).isTrue();
        assertThat(StatusEnvio.ENVIADO.isAfter(StatusEnvio.ENTREGUE)).isFalse();
        assertThat(StatusEnvio.ENVIADO.isAfter(null)).isTrue();
        // terminais nao regridem
        assertThat(StatusEnvio.ENTREGUE.isAfter(StatusEnvio.BOUNCE)).isFalse();
        assertThat(StatusEnvio.ENTREGUE.isAfter(StatusEnvio.FALHA)).isFalse();
        assertThat(StatusEnvio.ENTREGUE.isAfter(StatusEnvio.RECLAMACAO)).isFalse();
        // avanco para falha ainda vale a partir de estados nao-terminais
        assertThat(StatusEnvio.FALHA.isAfter(StatusEnvio.ENVIADO)).isTrue();
    }
}
