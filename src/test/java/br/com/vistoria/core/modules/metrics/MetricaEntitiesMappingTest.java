package br.com.vistoria.core.modules.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("metrics entities mapping")
class MetricaEntitiesMappingTest {

    @Test
    @DisplayName("MetricaEnvio holds its fields and derives the status enum")
    void metricaEnvio() {
        MetricaEnvio e = new MetricaEnvio();
        e.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        e.setMensagemId("m1");
        e.setTipoEnvio("email");
        e.setTipoComunicacao("conta");
        e.setExternalId("ext");
        e.setStatus("entregue");
        e.setStatusRaw("email.delivered");
        e.setBroker("resend");
        e.setDataEnvio(Instant.EPOCH);
        e.setStatusEm(Instant.EPOCH);
        e.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000009"));

        assertThat(e.getMensagemId()).isEqualTo("m1");
        assertThat(e.getExternalId()).isEqualTo("ext");
        assertThat(e.getBroker()).isEqualTo("resend");
        assertThat(e.getTenantId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000009"));
        assertThat(e.statusEnum()).isEqualTo(StatusEnvio.ENTREGUE);
        assertThat(e.getCriadoEm()).isNotNull();
    }

    @Test
    @DisplayName("MetricaEnvioDiario.Pk equals/hashCode by all three parts")
    void envioDiarioPk() {
        var a = new MetricaEnvioDiario.Pk();
        a.setDia(LocalDate.of(2026, 8, 1));
        a.setTipoEnvio("email");
        a.setTipoComunicacao("conta");
        var b = new MetricaEnvioDiario.Pk();
        b.setDia(LocalDate.of(2026, 8, 1));
        b.setTipoEnvio("email");
        b.setTipoComunicacao("conta");
        var c = new MetricaEnvioDiario.Pk();
        c.setDia(LocalDate.of(2026, 8, 2));
        c.setTipoEnvio("email");
        c.setTipoComunicacao("conta");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c).isNotEqualTo(null).isNotEqualTo("x").isEqualTo(a);

        MetricaEnvioDiario d = new MetricaEnvioDiario();
        d.setDia(LocalDate.of(2026, 8, 1));
        d.setCriados(5);
        assertThat(d.getCriados()).isEqualTo(5);
    }

    @Test
    @DisplayName("MetricaSolicitacaoDiario.Pk equals/hashCode by dia + tipoServicoId")
    void solicitacaoDiarioPk() {
        var a = new MetricaSolicitacaoDiario.Pk();
        a.setDia(LocalDate.of(2026, 8, 1));
        a.setTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000007"));
        var b = new MetricaSolicitacaoDiario.Pk();
        b.setDia(LocalDate.of(2026, 8, 1));
        b.setTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000007"));
        var c = new MetricaSolicitacaoDiario.Pk();
        c.setDia(LocalDate.of(2026, 8, 1));
        c.setTipoServicoId(UUID.fromString("00000000-0000-0000-0000-000000000008"));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c).isNotEqualTo(null).isNotEqualTo("x").isEqualTo(a);

        MetricaSolicitacaoDiario d = new MetricaSolicitacaoDiario();
        d.setTipoServicoNome("Entrada");
        d.setConcluidas(2);
        d.setEmAndamento(1);
        assertThat(d.getTipoServicoNome()).isEqualTo("Entrada");
        assertThat(d.getConcluidas()).isEqualTo(2);
        assertThat(d.getEmAndamento()).isEqualTo(1);
    }

    @Test
    @DisplayName("MetricaRecebida record accessors")
    void metricaRecebida() {
        var m = new MetricaRecebida("evt", "ext", StatusEnvio.ENVIADO, "sent", Instant.EPOCH);
        assertThat(m.eventId()).isEqualTo("evt");
        assertThat(m.externalId()).isEqualTo("ext");
        assertThat(m.status()).isEqualTo(StatusEnvio.ENVIADO);
        assertThat(m.statusRaw()).isEqualTo("sent");
        assertThat(m.ocorridoEm()).isEqualTo(Instant.EPOCH);
    }
}
