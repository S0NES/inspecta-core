package br.com.vistoria.core.shared.exception;

/**
 * Já existe uma imobiliária com esse CNPJ. Mapeada para 409 no
 * {@code ApiExceptionHandler} (conflito com dados existentes) — não é um payload
 * malformado (400), é um conflito de estado.
 */
public class CnpjDuplicadoException extends RuntimeException {
    public CnpjDuplicadoException() { super("CNPJ ja cadastrado"); }
}
