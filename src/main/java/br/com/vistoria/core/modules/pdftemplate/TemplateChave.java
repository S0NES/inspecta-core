package br.com.vistoria.core.modules.pdftemplate;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Catalogo dos tipos de template e do contrato de dados de cada um.
 *
 * <p>Existe para responder duas perguntas no momento do save, nao no momento do render:
 * <b>quais campos o template e obrigado a exibir</b> e <b>quais campos ele pode usar</b>.</p>
 *
 * <p>Os obrigatorios nao sao burocracia: o laudo e um documento com valor legal e uma
 * imobiliaria nao pode publicar um template que omita a versao, o servico de origem ou os
 * ambientes vistoriados. Sem essa checagem o erro so apareceria na fila {@code q.pdf}, ja
 * com o laudo finalizado — tarde demais e no lugar errado.</p>
 *
 * <p>Os opcionais existem para o outro lado da checagem: qualquer variavel fora da uniao
 * (obrigatorios + opcionais) e erro de digitacao. {@code $\{ambiente\}} no lugar de
 * {@code $\{ambientes\}} renderiza vazio silenciosamente; e melhor recusar no save.</p>
 */
public enum TemplateChave {

    /** Laudo de vistoria: o documento gerado pela fila {@code q.pdf}. */
    laudo(
            // Obrigatorios — sem estes o documento nao se sustenta.
            Set.of("versao", "solicitacaoServicoId", "emitidoEm", "ambientes"),
            // Opcionais — marca da imobiliaria e complementos. Todos sao efetivamente
            // preenchidos pelo renderizador; o catalogo nao anuncia campo que chegaria vazio.
            // qrCodeGaleria/galeriaUrl vem vazios ("") quando o laudo nao tem fotos ou o link
            // publico nao pode ser emitido - o template decide se mostra algo com <#if ...?has_content>.
            Set.of("laudoId", "imobiliariaNome", "observacoesGerais", "rodape",
                    "qrCodeGaleria", "galeriaUrl"));

    private final Set<String> obrigatorios;
    private final Set<String> opcionais;

    TemplateChave(Set<String> obrigatorios, Set<String> opcionais) {
        this.obrigatorios = Set.copyOf(obrigatorios);
        this.opcionais = Set.copyOf(opcionais);
    }

    public Set<String> obrigatorios() {
        return obrigatorios;
    }

    public Set<String> opcionais() {
        return opcionais;
    }

    /** Uniao dos dois conjuntos: tudo que o template pode referenciar legitimamente. */
    public Set<String> permitidos() {
        Set<String> todos = new LinkedHashSet<>(obrigatorios);
        todos.addAll(opcionais);
        return Set.copyOf(todos);
    }

    public static Optional<TemplateChave> de(String chave) {
        return Arrays.stream(values()).filter(c -> c.name().equals(chave)).findFirst();
    }

    public static String conhecidas() {
        return String.join(", ", Arrays.stream(values()).map(Enum::name).toList());
    }
}
