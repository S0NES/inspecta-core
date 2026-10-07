package br.com.vistoria.core.modules.metrics;

import br.com.vistoria.core.modules.metrics.MetricsRepositories.EnvioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsService")
class MetricsServiceTest {

    @Mock private EnvioRepository envios;
    private MetricsService service;

    @BeforeEach
    void setUp() {
        service = new MetricsService(envios);
    }


    @Test
    @DisplayName("registrarCriacao inserts a new row with normalized dimensions")
    void registrarCriacao_new() {
        when(envios.findByMensagemId("reset-1-2")).thenReturn(Optional.empty());
        Instant quando = Instant.parse("2026-08-01T10:00:00Z");

        service.registrarCriacao("reset-1-2", "EMAIL", "Conta", quando);

        ArgumentCaptor<MetricaEnvio> c = ArgumentCaptor.forClass(MetricaEnvio.class);
        verify(envios).saveAndFlush(c.capture());
        assertThat(c.getValue().getMensagemId()).isEqualTo("reset-1-2");
        assertThat(c.getValue().getTipoEnvio()).isEqualTo("email");
        assertThat(c.getValue().getTipoComunicacao()).isEqualTo("conta");
        assertThat(c.getValue().getStatus()).isEqualTo("criado");
        assertThat(c.getValue().getDataCriacao()).isEqualTo(quando);
    }

    @Test
    @DisplayName("registrarCriacao is a no-op on replay")
    void registrarCriacao_replay() {
        when(envios.findByMensagemId("dup")).thenReturn(Optional.of(new MetricaEnvio()));

        service.registrarCriacao("dup", "email", "conta", null);

        verify(envios, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("registrarCriacao swallows a concurrent unique-violation")
    void registrarCriacao_race() {
        when(envios.findByMensagemId("race")).thenReturn(Optional.empty());
        when(envios.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        service.registrarCriacao("race", "email", "conta", null);
        // no exception
    }

    @Test
    @DisplayName("registrarCriacao falls back to defaults for blank/unknown dimensions")
    void registrarCriacao_defaults() {
        when(envios.findByMensagemId(any())).thenReturn(Optional.empty());

        service.registrarCriacao("m1", "  ", "marketingX", null);

        ArgumentCaptor<MetricaEnvio> c = ArgumentCaptor.forClass(MetricaEnvio.class);
        verify(envios).saveAndFlush(c.capture());
        assertThat(c.getValue().getTipoEnvio()).isEqualTo("desconhecido");
        assertThat(c.getValue().getTipoComunicacao()).isEqualTo("operacional");
    }

    @Test
    @DisplayName("registrarCriacao ignores a blank mensagemId")
    void registrarCriacao_blankId() {
        service.registrarCriacao("  ", "email", "conta", null);
        verifyNoInteractions(envios);
    }


    @Test
    @DisplayName("registrarEnvio stamps data_envio and external_id, advancing status")
    void registrarEnvio_existing() {
        MetricaEnvio e = new MetricaEnvio();
        e.setMensagemId("m1");
        e.setStatus("criado");
        when(envios.findByMensagemId("m1")).thenReturn(Optional.of(e));

        Instant quando = Instant.parse("2026-08-01T11:00:00Z");
        service.registrarEnvio("m1", "resend-xyz", "email", quando);

        assertThat(e.getDataEnvio()).isEqualTo(quando);
        assertThat(e.getExternalId()).isEqualTo("resend-xyz");
        assertThat(e.getStatus()).isEqualTo("enviado");
    }

    @Test
    @DisplayName("registrarEnvio creates a minimal row when creation was lost")
    void registrarEnvio_minimalRow() {
        when(envios.findByMensagemId("late")).thenReturn(Optional.empty());

        service.registrarEnvio("late", "ext-1", "sms", null);

        ArgumentCaptor<MetricaEnvio> c = ArgumentCaptor.forClass(MetricaEnvio.class);
        verify(envios).saveAndFlush(c.capture());
        assertThat(c.getValue().getMensagemId()).isEqualTo("late");
        assertThat(c.getValue().getTipoEnvio()).isEqualTo("sms");
        assertThat(c.getValue().getTipoComunicacao()).isEqualTo("operacional");
        assertThat(c.getValue().getDataEnvio()).isNotNull();
    }

    @Test
    @DisplayName("registrarEnvio does not regress a later status")
    void registrarEnvio_noStatusRegression() {
        MetricaEnvio e = new MetricaEnvio();
        e.setMensagemId("m1");
        e.setStatus("entregue");
        when(envios.findByMensagemId("m1")).thenReturn(Optional.of(e));

        service.registrarEnvio("m1", "ext", "email", null);

        assertThat(e.getStatus()).isEqualTo("entregue");
    }

    @Test
    @DisplayName("registrarEnvio ignores a blank mensagemId")
    void registrarEnvio_blank() {
        service.registrarEnvio(" ", "ext", "email", null);
        verifyNoInteractions(envios);
    }


    @Test
    @DisplayName("registrarStatus applies a forward status and stamps status_em / status_raw")
    void registrarStatus_apply() {
        MetricaEnvio e = new MetricaEnvio();
        e.setStatus("enviado");
        when(envios.findFirstByExternalIdOrderByIdDesc("ext")).thenReturn(Optional.of(e));

        Instant quando = Instant.parse("2026-08-01T12:00:00Z");
        service.registrarStatus("resend", "ext", StatusEnvio.ENTREGUE, "email.delivered", quando);

        assertThat(e.getStatus()).isEqualTo("entregue");
        assertThat(e.getBroker()).isEqualTo("resend");
        assertThat(e.getStatusRaw()).isEqualTo("email.delivered");
        assertThat(e.getStatusEm()).isEqualTo(quando);
    }

    @Test
    @DisplayName("registrarStatus with no matching envio logs and returns")
    void registrarStatus_noMatch() {
        when(envios.findFirstByExternalIdOrderByIdDesc("ext")).thenReturn(Optional.empty());

        service.registrarStatus("resend", "ext", StatusEnvio.ENTREGUE, "x", null);
        // no exception
    }

    @Test
    @DisplayName("registrarStatus keeps status_raw + status_em even for DESCONHECIDO")
    void registrarStatus_unknown() {
        MetricaEnvio e = new MetricaEnvio();
        e.setStatus("enviado");
        when(envios.findFirstByExternalIdOrderByIdDesc("ext")).thenReturn(Optional.of(e));

        service.registrarStatus("resend", "ext", StatusEnvio.DESCONHECIDO, "weird.event", null);

        assertThat(e.getStatus()).isEqualTo("enviado");
        assertThat(e.getStatusRaw()).isEqualTo("weird.event");
        assertThat(e.getStatusEm()).isNotNull();
    }

    @Test
    @DisplayName("registrarStatus ignores a blank externalId")
    void registrarStatus_blank() {
        service.registrarStatus("resend", " ", StatusEnvio.ENTREGUE, "x", null);
        verifyNoInteractions(envios);
    }
}
