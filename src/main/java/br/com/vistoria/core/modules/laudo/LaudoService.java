package br.com.vistoria.core.modules.laudo;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LaudoService {

    private final LaudoRepository laudos;
    private final OutboxService outbox;
    private final ObjectMapper mapper;

    @Transactional
    public Laudo criar(UUID servicoId) {
        Laudo l = new Laudo();
        l.setSolicitacaoServicoId(servicoId);
        return laudos.save(l);
    }

    @Transactional
    public Laudo atualizarConteudo(UUID laudoId, String conteudoJson) {
        Laudo l = get(laudoId);
        if (l.getStatus() == Laudo.Status.assinado)
            throw new IllegalStateException("Laudo assinado e imutavel. Use nova-versao. (409)");
        l.setConteudoJson(garantirUuids(conteudoJson));
        return l;
    }

    @Transactional
    public void finalizar(UUID laudoId) {
        Laudo l = get(laudoId);
        if (l.getStatus() == Laudo.Status.assinado) throw new IllegalStateException("Ja assinado");
        l.setStatus(Laudo.Status.finalizado);
        outbox.publish(EventTypes.PDF_REQUESTED, Map.of(
                "eventId", "pdf-" + laudoId + "-v" + l.getVersao(),
                "laudoId", laudoId,
                "versao", l.getVersao()));
    }

    /**
     * Finaliza um laudo cujo PDF já veio pronto de fora (importação Pleno, §6.1 do
     * PLENO_INTEGRATION_PROMPT). Diferente de {@link #finalizar}: NÃO publica
     * {@link EventTypes#PDF_REQUESTED} — gerar outro PDF por cima sobrescreveria o real
     * da Pleno. Chamar {@link #marcarPdf} ANTES deste método (primeiro o PDF, depois o status).
     *
     * @param origem valor de {@link Laudo.Origem} (ex.: {@code "importado_pleno"})
     */
    @Transactional
    public void finalizarComPdfExterno(UUID laudoId, String origem) {
        Laudo l = get(laudoId);
        if (l.getStatus() == Laudo.Status.assinado) throw new IllegalStateException("Ja assinado");
        l.setStatus(Laudo.Status.finalizado);
        l.setOrigem(Laudo.Origem.valueOf(origem));
        // SEM publicar EventTypes.PDF_REQUESTED — o PDF já foi anexado via marcarPdf().
    }

    @Transactional
    public Laudo novaVersao(UUID laudoId) {
        Laudo atual = get(laudoId);
        if (atual.getStatus() != Laudo.Status.assinado)
            throw new IllegalStateException("Nova versao apenas de laudo assinado");
        Laudo nova = new Laudo();
        nova.setSolicitacaoServicoId(atual.getSolicitacaoServicoId());
        nova.setVersao(atual.getVersao() + 1);
        nova.setConteudoJson(atual.getConteudoJson());
        return laudos.save(nova);
    }

    @Transactional
    public void marcarPdf(UUID laudoId, UUID pdfArquivoId) {
        Laudo l = get(laudoId);
        if (l.getStatus() == Laudo.Status.assinado) return; // T3.1: replay no-op, laudo assinado e imutavel
        l.setPdfArquivoId(pdfArquivoId);
    }

    @Transactional
    public void marcarAssinado(UUID laudoId) {
        Laudo l = get(laudoId);
        if (l.getStatus() == Laudo.Status.assinado) return; // T3.1: replay no-op, laudo assinado e imutavel
        l.setStatus(Laudo.Status.assinado);
        l.setAssinadoEm(java.time.Instant.now());
    }

    public Laudo get(UUID id) {
        return laudos.findById(id).orElseThrow(() -> new NotFoundException("Laudo " + id));
    }

    private String garantirUuids(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            for (JsonNode amb : root.path("ambientes")) {
                setUuid(amb);
                for (JsonNode item : amb.path("itens")) setUuid(item);
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("conteudo_json invalido", e);
        }
    }

    private void setUuid(JsonNode node) {
        if (node instanceof ObjectNode o && !o.hasNonNull("uuid"))
            o.put("uuid", UUID.randomUUID().toString());
    }
}
