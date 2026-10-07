package br.com.vistoria.core.modules.arquivo;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArquivoService {

    private final ArquivoRepository arquivos;
    private final OutboxService outbox;

    @Transactional
    public Arquivo registrar(String r2Key, String mime, Long tamanho, String ownerType, UUID ownerId, boolean processado) {
        return arquivos.findByR2Key(r2Key).orElseGet(() -> persistirNovo(r2Key, mime, tamanho, ownerType, ownerId, processado));
    }

    private Arquivo persistirNovo(String r2Key, String mime, Long tamanho, String ownerType, UUID ownerId, boolean processado) {
        Arquivo a = new Arquivo();
        a.setR2Key(r2Key);
        a.setMime(mime);
        a.setTamanho(tamanho);
        a.setOwnerType(ownerType);
        a.setOwnerId(ownerId);
        a.setProcessado(processado);
        try {
            return arquivos.save(a);
        } catch (DataIntegrityViolationException corrida) {
            return arquivos.findByR2Key(r2Key).orElseThrow(() -> corrida);
        }
    }

    @Transactional(readOnly = true)
    public Arquivo get(UUID id) {
        return arquivos.findById(id).orElseThrow(() -> new NotFoundException("Arquivo " + id));
    }

    @Transactional
    public void confirmarUpload(UUID arquivoId) {
        Arquivo a = get(arquivoId);
        if (a.getMime() != null && a.getMime().startsWith("image/")) {
            outbox.publish(EventTypes.IMAGE_UPLOADED, Map.of(
                    "eventId", "img-" + arquivoId,
                    "arquivoId", arquivoId,
                    "r2Key", a.getR2Key()));
        } else {
            a.setProcessado(true);
        }
    }

    @Transactional
    public void marcarProcessado(UUID arquivoId, String thumbKey) {
        arquivos.findById(arquivoId).ifPresent(a -> {
            a.setProcessado(true);
            if (thumbKey != null) a.setThumbKey(thumbKey);
        });
    }

    @Transactional
    public void salvarConteudo(UUID arquivoId, byte[] bytes) {
        Arquivo a = get(arquivoId);
        a.setConteudo(bytes);
        arquivos.save(a);

        if (a.getMime() != null && a.getMime().startsWith("image/")) {
            outbox.publish(EventTypes.IMAGE_UPLOADED, Map.of(
                    "eventId", "img-" + arquivoId,
                    "arquivoId", arquivoId,
                    "r2Key", a.getR2Key()));
        }

        // Offload para storage externa (S3/local, inspecta-messenger): TODO tipo de arquivo, nao so imagem
        // (ao contrario do pipeline de thumbnail acima). Prefixo de eventId distinto ("upload-")
        // porque os dois publishes podem ocorrer na mesma transacao para um upload de imagem.
        outbox.publish(EventTypes.ARQUIVO_UPLOADED, Map.of(
                "eventId", "upload-" + arquivoId,
                "arquivoId", arquivoId,
                "r2Key", a.getR2Key()));
    }

    /**
     * Registra que o arquivo foi enviado para a storage externa configurada (S3 ou local, T-storage).
     * Reusa r2_key (dominio: "opaque identity key, legacy name") como storageKey, e storageProvider
     * NULL->preenchido serve de flag de idempotencia (nao precisa coluna separada).
     */
    @Transactional
    public void marcarStorageOffload(UUID arquivoId, String provider, String storageKey) {
        Arquivo a = get(arquivoId);
        a.setR2Key(storageKey);
        a.setStorageProvider(provider);
        arquivos.save(a);
    }

    @Transactional(readOnly = true)
    public byte[] lerConteudo(UUID arquivoId) {
        Arquivo a = get(arquivoId);
        if (a.getConteudo() == null) {
            throw new NotFoundException("Conteudo nao encontrado para arquivo " + arquivoId);
        }
        return a.getConteudo();
    }

    /**
     * Lista as imagens (mime image/*) de um dono, na ordem de upload. Usado pela galeria
     * publica de um laudo (link tipo "imagens", T2.10) e pelo QR embutido no PDF: ambos
     * precisam do conjunto completo de fotos, nao de uma so.
     */
    @Transactional(readOnly = true)
    public List<Arquivo> listarImagensPorDono(String ownerType, UUID ownerId) {
        return arquivos.findByOwnerTypeAndOwnerIdOrderByIdAsc(ownerType, ownerId).stream()
                .filter(a -> a.getMime() != null && a.getMime().startsWith("image/"))
                .toList();
    }
}
