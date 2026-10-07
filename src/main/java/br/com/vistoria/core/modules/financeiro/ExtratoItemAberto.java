package br.com.vistoria.core.modules.financeiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Projeção de um {@link ExtratoItem} em aberto para a tela de Extrato (spec: extrato.md):
 * valor + data + endereço do imóvel + tipo de serviço, já com o join pronto no banco.
 */
public record ExtratoItemAberto(
        UUID id,
        UUID solicitacaoServicoId,
        UUID solicitacaoId,
        BigDecimal valor,
        Instant criadoEm,
        String enderecoImovel,
        String numeroImovel,
        String tipoServicoNome
) {}
