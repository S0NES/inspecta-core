package br.com.vistoria.core.modules.notify;

import br.com.vistoria.core.modules.notify.NotifyRepositories.MessageTemplateRepository;
import br.com.vistoria.core.modules.notify.NotifyRepositories.TemplateUtilidadeRepository;
import br.com.vistoria.core.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CRUD versionado dos templates de mensagem (e-mail/SMS/push).
 *
 * <p>Mesmo padrão de {@code PdfTemplateService}: uma versão publicada nunca é sobrescrita — toda
 * alteração grava {@code versao+1} sob o mesmo {@code uuid}. {@link NotifyService#template}
 * sempre resolve a maior versão ativa, então quem publica um evento não precisa saber a versão.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotifyTemplateService {

    private static final Set<String> CANAIS = Set.of("email", "sms", "push");

    private final MessageTemplateRepository repo;
    private final TemplateUtilidadeRepository utilidadeRepo;

    @Transactional(readOnly = true)
    public List<MessageTemplate> listar(String canal) {
        return repo.listarUltimaVersao(canal == null || canal.isBlank() ? null : canal);
    }

    /** Uma linha por utilidade transacional, com o template hoje vinculado. */
    public record UtilidadeView(String utilidade, String rotulo, List<String> variaveis,
                                UUID templateId, String templateNome) {}

    @Transactional(readOnly = true)
    public List<UtilidadeView> listarUtilidades() {
        Map<String, UUID> config = new HashMap<>();
        utilidadeRepo.findAll().forEach(e -> config.put(e.getUtilidade(), e.getTemplateId()));
        return Arrays.stream(TemplateUtilidade.values()).map(u -> {
            UUID tid = config.getOrDefault(u.name(), UUID.fromString(u.uuidPadrao()));
            String nome = repo.findFirstByUuidAndAtivoTrueOrderByVersaoDesc(tid)
                    .map(MessageTemplate::getNome).orElse("(template inativo)");
            return new UtilidadeView(u.name(), u.rotulo(), u.variaveis(), tid, nome);
        }).toList();
    }

    /**
     * Aponta uma utilidade para outro template de e-mail. Autorização (só ADMIN) é do controller.
     * O alvo precisa existir, estar ativo e ser do canal {@code email}.
     */
    @Transactional
    public void definirUtilidade(String utilidade, UUID templateId, UUID usuarioId) {
        TemplateUtilidade u = TemplateUtilidade.porNome(utilidade)
                .orElseThrow(() -> new IllegalArgumentException("utilidade desconhecida: '" + utilidade + "'"));
        MessageTemplate alvo = repo.findFirstByUuidAndAtivoTrueOrderByVersaoDesc(templateId)
                .orElseThrow(() -> new NotFoundException("Template de mensagem " + templateId));
        if (!"email".equals(alvo.getCanal())) {
            throw new IllegalArgumentException("A utilidade '" + utilidade + "' so aceita template de canal email");
        }
        TemplateUtilidadeEntity ent = utilidadeRepo.findById(utilidade).orElseGet(() -> {
            TemplateUtilidadeEntity novo = new TemplateUtilidadeEntity();
            novo.setUtilidade(utilidade);
            return novo;
        });
        ent.setTemplateId(templateId);
        ent.setAtualizadoPor(usuarioId);
        ent.setAtualizadoEm(Instant.now());
        utilidadeRepo.save(ent);
        log.info("[msg-template] utilidade '{}' -> template {} (por usuario {})", u.name(), templateId, usuarioId);
    }

    /** Maior versão ativa de um template — o que a UI mostra ao abrir. */
    @Transactional(readOnly = true)
    public MessageTemplate porUuid(UUID uuid) {
        return repo.findFirstByUuidAndAtivoTrueOrderByVersaoDesc(uuid)
                .orElseThrow(() -> new NotFoundException("Template de mensagem " + uuid));
    }

    @Transactional(readOnly = true)
    public List<MessageTemplate> historico(UUID uuid) {
        return repo.findByUuidOrderByVersaoDesc(uuid);
    }

    /** Cria um template novo (versão 1, UUID novo). */
    @Transactional
    public MessageTemplate criar(String canal, String nome, String assunto, String corpo) {
        validar(canal, nome, corpo);
        MessageTemplate t = new MessageTemplate();
        t.setUuid(UUID.randomUUID());
        t.setVersao(1);
        t.setCanal(canal);
        t.setNome(nome.trim());
        t.setAssunto(assunto == null || assunto.isBlank() ? null : assunto.trim());
        t.setCorpo(corpo);
        t.setCriadoEm(Instant.now());
        t.setAtualizadoEm(Instant.now());
        MessageTemplate salvo = repo.save(t);
        log.info("[msg-template] '{}' criado uuid={}", salvo.getNome(), salvo.getUuid());
        return salvo;
    }

    /** Publica uma nova versão de um template existente (nunca sobrescreve a anterior). */
    @Transactional
    public MessageTemplate novaVersao(UUID uuid, String nome, String assunto, String corpo) {
        MessageTemplate ultimo = repo.findFirstByUuidOrderByVersaoDesc(uuid)
                .orElseThrow(() -> new NotFoundException("Template de mensagem " + uuid));
        validar(ultimo.getCanal(), nome, corpo);

        MessageTemplate t = new MessageTemplate();
        t.setUuid(uuid);
        t.setVersao(ultimo.getVersao() + 1);
        t.setCanal(ultimo.getCanal());
        t.setNome(nome.trim());
        t.setAssunto(assunto == null || assunto.isBlank() ? null : assunto.trim());
        t.setCorpo(corpo);
        t.setCriadoEm(Instant.now());
        t.setAtualizadoEm(Instant.now());
        MessageTemplate salvo = repo.save(t);
        log.info("[msg-template] '{}' v{} publicada uuid={}", salvo.getNome(), salvo.getVersao(), uuid);
        return salvo;
    }

    /**
     * Desativa todas as versões de um template. Um publisher que ainda aponte para este UUID
     * passa a não resolver template — o inspecta-messenger registra o erro em vez de enviar em branco.
     */
    @Transactional
    public void desativar(UUID uuid) {
        List<MessageTemplate> versoes = repo.findByUuidOrderByVersaoDesc(uuid);
        if (versoes.isEmpty()) {
            throw new NotFoundException("Template de mensagem " + uuid);
        }
        versoes.forEach(v -> {
            v.setAtivo(false);
            v.setAtualizadoEm(Instant.now());
        });
        log.info("[msg-template] uuid={} desativado ({} versoes)", uuid, versoes.size());
    }

    private void validar(String canal, String nome, String corpo) {
        if (canal == null || !CANAIS.contains(canal)) {
            throw new IllegalArgumentException("canal invalido: '" + canal + "' (use email, sms ou push)");
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome obrigatorio");
        }
        if (corpo == null || corpo.isBlank()) {
            throw new IllegalArgumentException("corpo obrigatorio");
        }
    }
}
