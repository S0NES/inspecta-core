package br.com.vistoria.core.modules.contestacao;

import br.com.vistoria.core.modules.laudo.Laudo;
import br.com.vistoria.core.modules.laudo.LaudoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContestacaoService {

    public interface ContestacaoRepository extends JpaRepository<Contestacao, UUID> {}
    public interface ReparoRepository extends JpaRepository<Reparo, UUID> {}

    private final ContestacaoRepository contestacoes;
    private final LaudoService laudoService;
    private final ObjectMapper mapper;

    /** Sentinela: contestação do laudo inteiro (sem ponto específico no JSON). */
    static final UUID LAUDO_INTEIRO = new UUID(0L, 0L);

    @Transactional
    public Contestacao criar(UUID laudoId, UUID nodeUuid, UUID autorId, String texto) {
        Laudo laudo = laudoService.get(laudoId);
        UUID alvo = nodeUuid == null ? LAUDO_INTEIRO : nodeUuid;
        if (!alvo.equals(LAUDO_INTEIRO) && !nodeExiste(laudo.getConteudoJson(), alvo))
            throw new IllegalArgumentException("node_uuid nao existe no laudo");

        Contestacao c = new Contestacao();
        c.setLaudoId(laudoId);
        c.setNodeUuid(alvo);
        c.setAutorId(autorId);
        c.setTexto(texto);
        return contestacoes.save(c);
    }

    @Transactional
    public void resolver(UUID contestacaoId, boolean aceitar, String resolucao) {
        Contestacao c = contestacoes.findById(contestacaoId).orElseThrow();
        c.setStatus(aceitar ? Contestacao.Status.aceita : Contestacao.Status.rejeitada);
        c.setResolucao(resolucao);
        if (aceitar) {
            Laudo l = laudoService.get(c.getLaudoId());
            if (l.getStatus() == Laudo.Status.assinado) laudoService.novaVersao(c.getLaudoId());
        }
    }

    private boolean nodeExiste(String json, UUID uuid) {
        try {
            return busca(mapper.readTree(json), uuid.toString());
        } catch (Exception e) { return false; }
    }

    private boolean busca(JsonNode node, String uuid) {
        if (node.isObject() && uuid.equals(node.path("uuid").asText())) return true;
        for (JsonNode child : node) if (busca(child, uuid)) return true;
        return false;
    }
}
