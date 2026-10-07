package br.com.vistoria.core.modules.disponibilidade;

import br.com.vistoria.core.modules.disponibilidade.DisponibilidadeRepositories.ExcecaoRepository;
import br.com.vistoria.core.modules.disponibilidade.DisponibilidadeRepositories.VistoriadorRepository;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoRepositories.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Elegibilidade de vistoriadores como servico in-process (read-only).
 *
 * <p>Extraido do {@code inspecta-worker/AssignmentService} para que o {@code inspecta-api} possa bloquear
 * a confirmacao de pagamento quando nao ha vistoriador disponivel (decisao de negocio 4,
 * V3 tipagem/biletagem). O {@code AssignmentService} reusa os helpers publicos daqui
 * ({@link #atendeLocal}, {@link #diaBloqueado}, {@link #haversineKm}) em vez de duplicar
 * a logica de cidade / distancia / excecao.</p>
 */
@Service
@RequiredArgsConstructor
public class DisponibilidadeService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final VistoriadorRepository vistoriadores;
    private final ExcecaoRepository excecoes;
    private final ServicoRepository servicos;

    /**
     * Existe ao menos um vistoriador elegivel para a cidade/local informados e (se
     * {@code dataDesejada} != null) sem conflito de agenda naquele horario?
     *
     * @param cidadeId          cidade do imovel
     * @param lat               latitude do imovel (pode ser null)
     * @param lng               longitude do imovel (pode ser null)
     * @param duracaoEstimadaMin duracao estimada do servico em minutos
     * @param urgente           reservado para regras futuras de horizonte; nao altera o resultado hoje
     * @param dataDesejada       instante desejado da vistoria; null = sem checagem de horario
     */
    @Transactional(readOnly = true)
    public boolean existeVistoriadorElegivel(UUID cidadeId, Double lat, Double lng,
                                             int duracaoEstimadaMin, boolean urgente,
                                             Instant dataDesejada) {
        return escolher(cidadeId, lat, lng, duracaoEstimadaMin, dataDesejada).isPresent();
    }

    /**
     * Igual a {@link #existeVistoriadorElegivel}, mas devolve o vistoriador escolhido em vez de
     * um booleano — usado pelo hold síncrono do wizard B2C (Página 4 do prompt "4 telas B2C"):
     * ao contrário do fluxo B2B (atribuição assíncrona só pós-pagamento), o B2C reserva um
     * vistoriador ANTES do pagamento para não cobrar por um horário sem cobertura.
     *
     * <p>Simplificação deliberada: primeiro elegível na ordem de {@code findByAtivoTrue()}, sem
     * lock exclusivo nem expiração de hold — duas reservas concorrentes para o mesmo
     * vistoriador/janela têm a mesma janela de corrida que já existe hoje entre o guard de
     * checkout (leitura) e a atribuição assíncrona real; quem grava {@code dataAgendada} por
     * último em {@code SolicitacaoServico} "vence" a leitura de {@code agendaOcupada} do outro.
     * Não há reconciliação automática desse conflito raro — fica para uma iteração futura se
     * virar problema real.</p>
     */
    @Transactional(readOnly = true)
    public Optional<Vistoriador> escolher(UUID cidadeId, Double lat, Double lng,
                                          int duracaoEstimadaMin, Instant dataDesejada) {
        for (Vistoriador v : vistoriadores.findByAtivoTrue()) {
            if (!atendeLocal(v, cidadeId, lat, lng)) continue;
            if (dataDesejada != null && !janelaLivre(v.getId(), dataDesejada, duracaoEstimadaMin)) continue;
            return Optional.of(v);
        }
        return Optional.empty();
    }

    /**
     * Vistoriador ativo, cobre {@code cidadeId} em {@code cidades[]} e o imovel esta dentro do
     * raio {@code distanciaMaxKm}. Distancia passa livre quando {@code distanciaMaxKm}, a base do
     * vistoriador ou a coordenada do imovel forem nulas (espelha o antigo {@code dentroDistancia}).
     */
    public boolean atendeLocal(Vistoriador v, UUID cidadeId, Double lat, Double lng) {
        if (!v.isAtivo()) return false;
        if (v.getCidades() == null || !Arrays.asList(v.getCidades()).contains(cidadeId)) return false;
        return dentroDistancia(v, lat, lng);
    }

    /** {@code true} se o dia local (America/Sao_Paulo) esta coberto por uma excecao (ferias/bloqueio). */
    public boolean diaBloqueado(UUID vistoriadorId, LocalDate dia) {
        return !excecoes.findByVistoriadorIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
                vistoriadorId, dia, dia).isEmpty();
    }

    private boolean dentroDistancia(Vistoriador v, Double lat, Double lng) {
        if (v.getDistanciaMaxKm() == null || v.getLatBase() == null || lat == null) return true;
        double d = haversineKm(v.getLatBase(), v.getLngBase(), lat, lng);
        return d <= v.getDistanciaMaxKm().doubleValue();
    }

    private boolean janelaLivre(UUID vistoriadorId, Instant dataDesejada, int duracaoMin) {
        LocalDate dia = dataDesejada.atZone(ZONE).toLocalDate();
        if (diaBloqueado(vistoriadorId, dia)) return false;
        Instant fim = dataDesejada.plus(Duration.ofMinutes(duracaoMin));
        return servicos.agendaOcupada(vistoriadorId, dataDesejada, fim).isEmpty();
    }

    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
