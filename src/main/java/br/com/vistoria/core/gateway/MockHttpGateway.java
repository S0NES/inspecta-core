package br.com.vistoria.core.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@Profile("!prod")
@Slf4j
public class MockHttpGateway implements HttpGateway {

    private static final HttpGatewayResponse PADRAO = new HttpGatewayResponse(200, "");

    private final Queue<HttpGatewayResponse> proximasRespostas = new ConcurrentLinkedQueue<>();
    private final Queue<ScheduledResponse> respostasComAtraso = new ConcurrentLinkedQueue<>();

    private static class ScheduledResponse {
        final HttpGatewayResponse resposta;
        final long executarEm; // System.currentTimeMillis() when to execute

        ScheduledResponse(HttpGatewayResponse resposta, Duration atraso) {
            this.resposta = resposta;
            this.executarEm = System.currentTimeMillis() + atraso.toMillis();
        }

        boolean pronta() {
            return System.currentTimeMillis() >= executarEm;
        }
    }

    @Override
    public HttpGatewayResponse execute(HttpGatewayRequest request) throws InterruptedException {
        // Primeiro, tenta pega uma resposta agendada com atraso que ja esta pronta
        ScheduledResponse agendada = respostasComAtraso.peek();
        if (agendada != null && agendada.pronta()) {
            respostasComAtraso.poll();
            log.info("[http-gateway-mock] {} {} -> status={} body={} (delayed response ready)",
                    request.method(), request.uri(), agendada.resposta.status(), agendada.resposta.body());
            return agendada.resposta;
        }

        // Se temos uma resposta agendada mas ainda nao esta pronta, simula o timeout se aplicavel
        if (agendada != null) {
            long agora = System.currentTimeMillis();
            long tempoRestante = agendada.executarEm - agora;
            Duration timeout = request.timeout();
            long timeoutMs = timeout != null ? timeout.toMillis() : Long.MAX_VALUE;

            // Aguarda ate o minimo entre: tempo restante para resposta estar pronta e timeout
            long tempoEspera = Math.min(tempoRestante, timeoutMs);
            if (tempoEspera > 0) {
                Thread.sleep(tempoEspera);
            }

            // Verifica se o timeout estourou (tempo de espera foi limitado pelo timeout)
            if (tempoEspera == timeoutMs && tempoRestante > timeoutMs) {
                // Timeout ocorreu antes da resposta ficar pronta
                log.info("[http-gateway-mock] {} {} -> timeout de {}ms estourou (resposta agendada em {}ms)",
                        request.method(), request.uri(), timeoutMs, tempoRestante);
                throw new RuntimeException("Request timeout after " + timeoutMs + "ms");
            }

            // Resposta ja deveria estar pronta agora
            if (agendada.pronta()) {
                respostasComAtraso.poll();
                log.info("[http-gateway-mock] {} {} -> status={} body={} (delayed response ready after wait)",
                        request.method(), request.uri(), agendada.resposta.status(), agendada.resposta.body());
                return agendada.resposta;
            } else {
                // Erro: nao deveria chegar aqui
                throw new RuntimeException("Scheduled response not ready after wait");
            }
        }

        // Caso normal: tira da fila de respostas simples (sem atraso)
        HttpGatewayResponse resposta = proximasRespostas.poll();
        if (resposta == null) resposta = PADRAO;
        log.info("[http-gateway-mock] {} {} -> status={} body={}",
                request.method(), request.uri(), resposta.status(), resposta.body());
        return resposta;
    }

    public void programarProxima(HttpGatewayResponse resposta) {
        proximasRespostas.add(resposta);
    }

    public void programarProxima(int status, String body) {
        programarProxima(new HttpGatewayResponse(status, Map.of(), body));
    }

    /**
     * Agenda a proxima resposta para ser entregue apos o atraso especificado.
     * Test-only: quando execute() e chamado com um request que tem timeout,
     * e o atraso e maior que o timeout, a requisicao vai lançar RuntimeException
     * simulando o comportamento real de timeout.
     *
     * @param atraso Quanto tempo aguardar antes de retornar a resposta
     * @param resposta A resposta a retornar
     */
    public void programarProximaComAtraso(Duration atraso, HttpGatewayResponse resposta) {
        respostasComAtraso.add(new ScheduledResponse(resposta, atraso));
    }

    public void programarProximaComAtraso(Duration atraso, int status, String body) {
        programarProximaComAtraso(atraso, new HttpGatewayResponse(status, Map.of(), body));
    }

    public void limpar() {
        proximasRespostas.clear();
        respostasComAtraso.clear();
    }
}
