package br.com.vistoria.core.modules.metrics;

import br.com.vistoria.core.modules.metrics.MetricsRepositories.EnvioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * Ciclo de vida de uma metrica de envio, gravada exclusivamente pelo inspecta-worker a
 * partir da fila {@code q.metrics}. Todos os metodos sao idempotentes (entrega
 * at-least-once): {@code mensagem_id} unico, {@code DataIntegrityViolationException}
 * engolida, avanco de status forward-only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private static final Set<String> TIPOS_COMUNICACAO =
            Set.of("conta", "assinatura_laudo", "operacional", "marketing");
    private static final String COMUNICACAO_PADRAO = "operacional";
    private static final String TIPO_ENVIO_PADRAO = "desconhecido";

    private final EnvioRepository envios;

    /** Registra a criacao da mensagem (data_criacao). Chamado por cada evento {@code notify.requested.*}. */
    @Transactional
    public void registrarCriacao(String mensagemId, String tipoEnvio, String tipoComunicacao, Instant quando) {
        if (mensagemId == null || mensagemId.isBlank()) {
            log.warn("[metrics] registrarCriacao sem mensagemId, ignorado");
            return;
        }
        if (envios.findByMensagemId(mensagemId).isPresent()) return;

        MetricaEnvio e = new MetricaEnvio();
        e.setMensagemId(mensagemId);
        e.setTipoEnvio(blankTo(tipoEnvio, TIPO_ENVIO_PADRAO));
        e.setTipoComunicacao(normalizarComunicacao(tipoComunicacao));
        e.setStatus(StatusEnvio.CRIADO.dbValue());
        if (quando != null) e.setDataCriacao(quando);
        try {
            envios.saveAndFlush(e);
        } catch (DataIntegrityViolationException dup) {
            log.info("[metrics] criacao duplicada ignorada mensagemId={}", mensagemId);
        }
    }

    /** Registra o envio efetivo pelo inspecta-messenger (data_envio + external_id). */
    @Transactional
    public void registrarEnvio(String mensagemId, String externalId, String canalFallback, Instant quando) {
        if (mensagemId == null || mensagemId.isBlank()) return;
        MetricaEnvio e = envios.findByMensagemId(mensagemId).orElseGet(() -> {
            log.warn("[metrics] envio sem criacao previa, criando linha minima mensagemId={}", mensagemId);
            MetricaEnvio novo = new MetricaEnvio();
            novo.setMensagemId(mensagemId);
            novo.setTipoEnvio(blankTo(canalFallback, TIPO_ENVIO_PADRAO));
            novo.setTipoComunicacao(COMUNICACAO_PADRAO);
            return novo;
        });
        if (e.getDataEnvio() == null) e.setDataEnvio(quando != null ? quando : Instant.now());
        if (e.getExternalId() == null && externalId != null && !externalId.isBlank()) {
            e.setExternalId(externalId);
        }
        if (StatusEnvio.ENVIADO.isAfter(e.statusEnum())) {
            e.setStatus(StatusEnvio.ENVIADO.dbValue());
        }
        e.setAtualizadoEm(Instant.now());
        try {
            envios.saveAndFlush(e);
        } catch (DataIntegrityViolationException dup) {
            log.info("[metrics] envio concorrente ignorado mensagemId={}", mensagemId);
        }
    }

    /** Aplica o status reportado por um webhook de provedor, casado por external_id. */
    @Transactional
    public void registrarStatus(String broker, String externalId, StatusEnvio status,
                                String statusRaw, Instant quando) {
        if (externalId == null || externalId.isBlank()) return;
        var opt = envios.findFirstByExternalIdOrderByIdDesc(externalId);
        if (opt.isEmpty()) {
            log.warn("[metrics] status de webhook sem envio correspondente broker={} externalId={}", broker, externalId);
            return;
        }
        MetricaEnvio e = opt.get();
        e.setBroker(broker);
        e.setStatusRaw(statusRaw);
        if (status == StatusEnvio.DESCONHECIDO) {
            log.warn("[metrics] status desconhecido do provedor broker={} raw={}", broker, statusRaw);
        } else if (status.isAfter(e.statusEnum())) {
            e.setStatus(status.dbValue());
        }
        e.setStatusEm(quando != null ? quando : Instant.now());
        e.setAtualizadoEm(Instant.now());
    }

    private String normalizarComunicacao(String tipoComunicacao) {
        if (tipoComunicacao == null || tipoComunicacao.isBlank()) return COMUNICACAO_PADRAO;
        String t = tipoComunicacao.trim().toLowerCase();
        if (TIPOS_COMUNICACAO.contains(t)) return t;
        log.warn("[metrics] tipo_comunicacao desconhecido '{}', usando '{}'", tipoComunicacao, COMUNICACAO_PADRAO);
        return COMUNICACAO_PADRAO;
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim().toLowerCase();
    }
}
