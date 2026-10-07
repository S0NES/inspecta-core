package br.com.vistoria.core.modules.pdftemplate;

import br.com.vistoria.core.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD e resolucao dos templates de PDF.
 *
 * <p>Toda escrita passa por {@link TemplateValidator}. A validacao vive aqui, e nao na camada
 * REST, de proposito: nenhum caminho de escrita (inspecta-api, seed, migracao de dados, teste) pode
 * gravar um template que o renderizador nao conseguiria usar depois.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfTemplateService {

    private final PdfTemplateRepository templates;
    private final TemplateValidator validator;

    /**
     * Valida e grava uma <b>nova versao</b> do template.
     *
     * <p>Nunca sobrescreve uma versao existente: a versao e sempre a maior ja gravada mais um,
     * pelo mesmo motivo de {@code notify.templates} — um laudo assinado e imutavel e precisa
     * continuar sendo possivel saber com que template ele foi gerado.</p>
     *
     * @throws TemplateInvalidoException se o template nao passar na validacao; nada e gravado.
     */
    @Transactional
    public PdfTemplate salvar(PdfTemplate template) {
        validator.validar(template);

        template.setId(null);
        template.setVersao(proximaVersao(template.getChave(), template.getImobiliariaId()));
        template.setCriadoEm(Instant.now());
        template.setAtualizadoEm(Instant.now());

        PdfTemplate salvo = templates.save(template);
        log.info("[pdf-template] '{}' v{} salvo para {}", salvo.getChave(), salvo.getVersao(),
                salvo.isGlobal() ? "template global" : "imobiliaria " + salvo.getImobiliariaId());
        return salvo;
    }

    /**
     * Template a usar para uma imobiliaria: o dela se houver, senao o global.
     *
     * <p>Vazio significa "nenhum template cadastrado", nao um erro — o chamador cai no template
     * embutido em codigo. E o que mantem a geracao de laudo funcionando sem nenhum cadastro.</p>
     */
    @Transactional(readOnly = true)
    public Optional<PdfTemplate> resolver(String chave, UUID imobiliariaId) {
        if (imobiliariaId != null) {
            Optional<PdfTemplate> daImobiliaria = templates
                    .findFirstByChaveAndImobiliariaIdAndAtivoTrueOrderByVersaoDesc(chave, imobiliariaId);
            if (daImobiliaria.isPresent()) {
                return daImobiliaria;
            }
        }
        return templates.findFirstByChaveAndImobiliariaIdIsNullAndAtivoTrueOrderByVersaoDesc(chave);
    }

    /**
     * Template a usar para uma imobiliaria, considerando o vinculo explicito.
     *
     * <p>Se {@code templateIdExplicito} aponta para um template valido (existe, ativo, mesma
     * chave), ele vence — e o que o admin escolheu na edicao da imobiliaria. Vinculo quebrado
     * (template apagado, desativado ou de outra chave) e ignorado: cai na resolucao normal em
     * vez de deixar a imobiliaria sem documento.</p>
     */
    @Transactional(readOnly = true)
    public Optional<PdfTemplate> resolverParaImobiliaria(String chave, UUID imobiliariaId, UUID templateIdExplicito) {
        if (templateIdExplicito != null) {
            Optional<PdfTemplate> explicito = templates.findById(templateIdExplicito)
                    .filter(PdfTemplate::isAtivo)
                    .filter(t -> chave.equals(t.getChave()));
            if (explicito.isPresent()) {
                return explicito;
            }
        }
        return resolver(chave, imobiliariaId);
    }

    @Transactional(readOnly = true)
    public List<PdfTemplate> historico(String chave, UUID imobiliariaId) {
        return templates.findByChaveAndImobiliariaIdOrderByVersaoDesc(chave, imobiliariaId);
    }

    /** Todos os templates ativos (a UI de Templates e o Select de vinculo da imobiliaria). */
    @Transactional(readOnly = true)
    public List<PdfTemplate> listarAtivos() {
        return templates.findByAtivoTrueOrderByChaveAscNomeAsc();
    }

    @Transactional(readOnly = true)
    public PdfTemplate porUuid(UUID uuid) {
        return get(uuid);
    }

    @Transactional(readOnly = true)
    public PdfTemplate get(UUID id) {
        return templates.findById(id)
                .orElseThrow(() -> new NotFoundException("PdfTemplate " + id));
    }

    /**
     * Desativa uma versao. A imobiliaria volta a cair no template global (ou no embutido),
     * em vez de ficar sem documento.
     */
    @Transactional
    public void desativar(UUID id) {
        PdfTemplate t = get(id);
        t.setAtivo(false);
        t.setAtualizadoEm(Instant.now());
        log.info("[pdf-template] '{}' v{} desativado", t.getChave(), t.getVersao());
    }

    private int proximaVersao(String chave, UUID imobiliariaId) {
        Optional<PdfTemplate> ultimo = imobiliariaId == null
                ? templates.findFirstByChaveAndImobiliariaIdIsNullOrderByVersaoDesc(chave)
                : templates.findFirstByChaveAndImobiliariaIdOrderByVersaoDesc(chave, imobiliariaId);
        return ultimo.map(t -> t.getVersao() + 1).orElse(1);
    }
}
