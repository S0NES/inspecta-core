package br.com.vistoria.core.modules.contrato;

/**
 * Não há contrato ativo / serviço configurado para resolver o preço de biletagem.
 * O inspecta-api mapeia para 422 {@code {codigo:"CONTRATO_NAO_CONFIGURADO"}} — o mesmo
 * código do preview. Nunca há fallback para preço manual.
 */
public class ContratoNaoConfiguradoException extends RuntimeException {
    public ContratoNaoConfiguradoException(String message) {
        super(message);
    }
}
