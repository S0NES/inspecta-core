package br.com.vistoria.core.modules.imobiliaria.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateImobiliariaRequest(
        @NotBlank String razaoSocial,
        String nomeFantasia,
        @NotBlank @Pattern(regexp = "\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}") String cnpj,
        String pix,
        String emailFinanceiro,
        String telefone,
        /** Endereço próprio da imobiliária (V2). Mesma convenção do imóvel; nunca vai para a Pleno. */
        String cep,
        String endereco,
        String numero,
        String complemento,
        UUID cidadeId,
        /** Cadastro externo: quando presente, vincula ao Cliente ja existente na Pleno com esse cli_codigo em vez de espelhar um novo. */
        @Pattern(regexp = "\\d+", message = "plenoIdExterno deve ser numerico") String plenoIdExterno,
        /** Biletagem definida no wizard de cadastro (step Pagamento). Opcional para compat. */
        @Valid ContratoCreateRequest contrato
) {
    /** Compat: chamadas anteriores sem cadastro externo nem endereço. */
    public CreateImobiliariaRequest(String razaoSocial, String nomeFantasia, String cnpj,
                                    String pix, String emailFinanceiro, String telefone) {
        this(razaoSocial, nomeFantasia, cnpj, pix, emailFinanceiro, telefone,
                null, null, null, null, null, null, null);
    }

    /** Compat: chamadas anteriores com cadastro externo mas sem endereço. */
    public CreateImobiliariaRequest(String razaoSocial, String nomeFantasia, String cnpj,
                                    String pix, String emailFinanceiro, String telefone, String plenoIdExterno) {
        this(razaoSocial, nomeFantasia, cnpj, pix, emailFinanceiro, telefone,
                null, null, null, null, null, plenoIdExterno, null);
    }

    /** Compat: chamadas com endereço/pleno mas sem contrato embutido. */
    public CreateImobiliariaRequest(String razaoSocial, String nomeFantasia, String cnpj,
                                    String pix, String emailFinanceiro, String telefone,
                                    String cep, String endereco, String numero, String complemento,
                                    UUID cidadeId, String plenoIdExterno) {
        this(razaoSocial, nomeFantasia, cnpj, pix, emailFinanceiro, telefone,
                cep, endereco, numero, complemento, cidadeId, plenoIdExterno, null);
    }

    public record ContratoCreateRequest(
            String modo,
            BigDecimal percentual,
            BigDecimal percentualMinimo,
            List<GrupoItem> grupos,
            List<FaixaItem> faixas
    ) {}

    public record GrupoItem(UUID servicoId, BigDecimal percentual, BigDecimal percentualMinimo) {}

    public record FaixaItem(UUID servicoId, Short faixaId,
                            BigDecimal valorSemMobilia, BigDecimal valorComMobilia,
                            BigDecimal paStepMetragem, BigDecimal paStepValor) {}
}
