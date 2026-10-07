package br.com.vistoria.core.modules.pdftemplate;

import java.util.List;

/**
 * Recusa de um template no momento do save.
 *
 * <p>Carrega <b>todos</b> os problemas encontrados, nao apenas o primeiro: quem esta editando
 * um template quer corrigir tudo de uma vez, e nao descobrir o proximo erro a cada tentativa.</p>
 */
public class TemplateInvalidoException extends RuntimeException {

    private final transient List<String> erros;

    public TemplateInvalidoException(List<String> erros) {
        super("Template invalido: " + String.join("; ", erros));
        this.erros = List.copyOf(erros);
    }

    public List<String> getErros() {
        return erros;
    }
}
