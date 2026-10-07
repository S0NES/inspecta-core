package br.com.vistoria.core.modules.imobiliaria.dto;

import br.com.vistoria.core.modules.imobiliaria.Imobiliaria;

import java.util.UUID;

public record ImobiliariaResponse(UUID id, String razaoSocial, String nomeFantasia,
                                  String cnpj, boolean ativa,
                                  String pix, String emailFinanceiro, String telefone,
                                  String cep, String endereco, String numero, String complemento, UUID cidadeId,
                                  UUID pdfTemplateId) {
    /** Compat: chamadas anteriores sem os campos financeiros. */
    public ImobiliariaResponse(UUID id, String razaoSocial, String nomeFantasia, String cnpj, boolean ativa) {
        this(id, razaoSocial, nomeFantasia, cnpj, ativa, null, null, null, null, null, null, null, null, null);
    }

    /** Compat: chamadas anteriores sem o vinculo de template PDF nem endereço. */
    public ImobiliariaResponse(UUID id, String razaoSocial, String nomeFantasia, String cnpj, boolean ativa,
                               String pix, String emailFinanceiro, String telefone) {
        this(id, razaoSocial, nomeFantasia, cnpj, ativa, pix, emailFinanceiro, telefone,
                null, null, null, null, null, null);
    }

    /** Compat: chamadas anteriores sem endereço. */
    public ImobiliariaResponse(UUID id, String razaoSocial, String nomeFantasia, String cnpj, boolean ativa,
                               String pix, String emailFinanceiro, String telefone, UUID pdfTemplateId) {
        this(id, razaoSocial, nomeFantasia, cnpj, ativa, pix, emailFinanceiro, telefone,
                null, null, null, null, null, pdfTemplateId);
    }

    public static ImobiliariaResponse from(Imobiliaria i) {
        return new ImobiliariaResponse(i.getId(), i.getRazaoSocial(),
                i.getNomeFantasia(), i.getCnpj(), i.isAtiva(),
                i.getPix(), i.getEmailFinanceiro(), i.getTelefone(),
                i.getCep(), i.getEndereco(), i.getNumero(), i.getComplemento(), i.getCidadeId(),
                i.getPdfTemplateId());
    }
}
