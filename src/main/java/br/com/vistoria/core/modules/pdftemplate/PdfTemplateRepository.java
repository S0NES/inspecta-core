package br.com.vistoria.core.modules.pdftemplate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PdfTemplateRepository extends JpaRepository<PdfTemplate, UUID> {

    List<PdfTemplate> findByAtivoTrueOrderByChaveAscNomeAsc();

    /** Versao ativa mais recente do template de uma imobiliaria. */
    Optional<PdfTemplate> findFirstByChaveAndImobiliariaIdAndAtivoTrueOrderByVersaoDesc(
            String chave, UUID imobiliariaId);

    /** Versao ativa mais recente do template global (fallback). */
    Optional<PdfTemplate> findFirstByChaveAndImobiliariaIdIsNullAndAtivoTrueOrderByVersaoDesc(
            String chave);

    /** Maior versao ja existente (ativa ou nao), para calcular a proxima. */
    Optional<PdfTemplate> findFirstByChaveAndImobiliariaIdOrderByVersaoDesc(
            String chave, UUID imobiliariaId);

    Optional<PdfTemplate> findFirstByChaveAndImobiliariaIdIsNullOrderByVersaoDesc(String chave);

    List<PdfTemplate> findByChaveAndImobiliariaIdOrderByVersaoDesc(String chave, UUID imobiliariaId);
}
