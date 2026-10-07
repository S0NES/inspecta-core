package br.com.vistoria.core.modules.solicitacao.dto;

import br.com.vistoria.core.modules.solicitacao.Solicitacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Corpo de {@code POST /v1/solicitacoes}.
 *
 * <p>Toda solicitação tem cliente + imóvel. A {@code imobiliaria_id} da solicitação
 * é derivada de {@code imovel.imobiliaria_id} no serviço — não vem do corpo. O
 * cliente tem de pertencer à mesma imobiliária do imóvel.</p>
 */
public record CreateSolicitacaoRequest(
        /** Opcional (V24/D1): o fluxo B2B entra por Imobiliária → Imóvel; o cliente-pessoa deixou
         *  de ser gate. Quando != null, tem de pertencer à mesma imobiliária do imóvel. */
        UUID clienteId,
        @NotNull UUID imovelId,
        Boolean urgente,
        String chavesRetirada,
        String chavesDevolucao,
        /** V4 (D2): nível solicitação (não por serviço). Obrigatórios só quando há imobiliária
         *  e o contrato ativo é modo percentual — validado no serviço. */
        BigDecimal valorAluguel,
        Boolean mobiliado,
        @NotNull Solicitacao.Origem origem,
        @NotEmpty List<ServicoItem> servicos,
        /** ≥1 pessoa (nome/cpf/e-mail válidos). São os signatários da assinatura (Autentique). */
        @NotEmpty List<@Valid SolicitacaoIntervenienteItem> intervenientes,
        /** Cadastro externo: vincula a solicitação Pleno já existente com esse sol_codigo. */
        @Pattern(regexp = "\\d+", message = "plenoIdExterno deve ser numerico") String plenoIdExterno
) {
    public record ServicoItem(
            @NotNull UUID tipoServicoId,
            BigDecimal basePercentual,
            /** Data/hora desejada para a vistoria deste serviço (opcional). */
            Instant dataDesejada
    ) {}

    public record SolicitacaoIntervenienteItem(
            String papelSlug,   // locador | locatario | fiador (slug ativo em interveniente_papeis)
            String nome,
            String cpf,
            String email,
            /** Opcional — 10-11 dígitos. */
            String telefone,
            /** Marca este interveniente como acompanhante da vistoria (0+; opcional). */
            Boolean acompanhante
    ) {
        /** Compat: chamadas antigas sem telefone/acompanhante. */
        public SolicitacaoIntervenienteItem(String papelSlug, String nome, String cpf, String email) {
            this(papelSlug, nome, cpf, email, null, null);
        }
    }
}
