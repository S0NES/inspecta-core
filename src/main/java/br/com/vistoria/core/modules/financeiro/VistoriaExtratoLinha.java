package br.com.vistoria.core.modules.financeiro;

import br.com.vistoria.core.modules.solicitacao.SolicitacaoServico;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma linha do extrato granular (spec {@code extrato.md}): um {@code solicitacao_servico}
 * com os joins de imóvel / imobiliária / vistoriador. {@code laudoId} é preenchido
 * depois no serviço (não entra na projeção JPQL — um serviço pode ter várias versões).
 */
public record VistoriaExtratoLinha(
        UUID id,
        UUID solicitacaoId,
        UUID imobiliariaId,
        String imobiliariaNome,
        String endereco,
        String tipoServico,
        String vistoriador,
        SolicitacaoServico.Status status,
        Instant criadoEm,
        BigDecimal valor,
        UUID laudoId
) {
    /** Construtor da projeção JPQL (sem laudo). */
    public VistoriaExtratoLinha(
            UUID id,
            UUID solicitacaoId,
            UUID imobiliariaId,
            String imobiliariaNome,
            String endereco,
            String tipoServico,
            String vistoriador,
            SolicitacaoServico.Status status,
            Instant criadoEm,
            BigDecimal valor
    ) {
        this(id, solicitacaoId, imobiliariaId, imobiliariaNome, endereco, tipoServico,
                vistoriador, status, criadoEm, valor, null);
    }

    public VistoriaExtratoLinha withLaudo(UUID laudo) {
        return new VistoriaExtratoLinha(id, solicitacaoId, imobiliariaId, imobiliariaNome,
                endereco, tipoServico, vistoriador, status, criadoEm, valor, laudo);
    }
}
