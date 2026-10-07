package br.com.vistoria.core.shared.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookRecebidoService")
class WebhookRecebidoServiceTest {

    @Mock
    private WebhookRecebidoRepository repository;

    private WebhookRecebidoService service;

    @BeforeEach
    void setUp() {
        service = new WebhookRecebidoService(repository);
    }

    @Test
    @DisplayName("evento novo é gravado e devolvido")
    void receber_novo() {
        when(repository.inserirSeNovo("autentique", "evt-123", "{\"a\":1}")).thenReturn(1);

        Optional<WebhookRecebido> result = service.receber("autentique", "evt-123", "{\"a\":1}");

        assertThat(result).isPresent();
        assertThat(result.get().getOrigem()).isEqualTo("autentique");
        assertThat(result.get().getEventId()).isEqualTo("evt-123");
        assertThat(result.get().getPayload()).isEqualTo("{\"a\":1}");
        verify(repository).inserirSeNovo("autentique", "evt-123", "{\"a\":1}");
    }

    @Test
    @DisplayName("reenvio (event_id já existente) devolve empty sem lançar — não envenena a transação")
    void receber_duplicado() {
        when(repository.inserirSeNovo("pagamento-mock", "evt-1", "{}")).thenReturn(0);

        assertThat(service.receber("pagamento-mock", "evt-1", "{}")).isEmpty();
    }

    @Test
    @DisplayName("falha de infraestrutura propaga")
    void receber_erroPropaga() {
        when(repository.inserirSeNovo("x", "evt", "{}")).thenThrow(new IllegalStateException("db fora"));

        assertThatThrownBy(() -> service.receber("x", "evt", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db fora");
    }
}
