package br.com.vistoria.core.modules.financeiro;

import java.math.BigDecimal;
import java.util.List;

/** Página do extrato por vistoria + totais do recorte (não só da página corrente). */
public record VistoriasPage(
        List<VistoriaExtratoLinha> content,
        long totalElements,
        int totalPages,
        int number,
        BigDecimal totalFaturado
) {}
