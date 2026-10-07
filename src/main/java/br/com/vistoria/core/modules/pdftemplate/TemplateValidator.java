package br.com.vistoria.core.modules.pdftemplate;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida um template de PDF <b>no momento do save</b>.
 *
 * <p>O ponto e mover a falha para onde ela e barata. Um template quebrado que passa pelo save
 * so falha depois, dentro do consumer de {@code q.pdf}, num laudo ja finalizado, sem ninguem
 * olhando — vira retry, DLQ e um laudo sem PDF. Aqui a falha volta para quem esta editando,
 * com a lista completa do que corrigir.</p>
 *
 * <p>Cinco familias de checagem:</p>
 * <ol>
 *   <li><b>Campos obrigatorios</b> — o template referencia tudo que {@link TemplateChave}
 *       exige para aquele tipo de documento.</li>
 *   <li><b>Campos desconhecidos</b> — nada fora do catalogo. Pega erro de digitacao, que de
 *       outro modo renderiza vazio em silencio.</li>
 *   <li><b>Sintaxe FreeMarker</b> — o proprio parser do FreeMarker, entao e exato.</li>
 *   <li><b>Seguranca</b> — ?new/?eval/?api sao caminhos conhecidos de execucao de codigo via
 *       template; script e handlers on* nao tem sentido num PDF.</li>
 *   <li><b>Recursos externos</b> — o renderizador ja bloqueia (ADR-001), entao referencia
 *       remota so produziria um documento furado. Melhor recusar e dizer o porque.</li>
 * </ol>
 *
 * <p><b>Limite conhecido:</b> a extracao de variaveis e um scan lexical, nao a AST do
 * FreeMarker (que nao e API publica). Ela cobre interpolacoes e diretivas comuns; expressoes
 * muito exoticas podem escapar da checagem de "campo desconhecido". A checagem de
 * obrigatorios, que e a que tem peso legal, nao depende disso — e busca direta pelo nome.</p>
 */
@Component
public class TemplateValidator {

    /** Comentarios FreeMarker: removidos antes de qualquer analise. */
    private static final Pattern COMENTARIO = Pattern.compile("<#--.*?-->", Pattern.DOTALL);

    /** Interpolacoes e diretivas: onde variaveis podem aparecer. */
    private static final Pattern EXPRESSAO =
            Pattern.compile("\\$\\{([^}]*)}|<[#@]([^>]*)>", Pattern.DOTALL);

    private static final Pattern LITERAL = Pattern.compile("\"[^\"]*\"|'[^']*'");

    /** Builtin apos '?' (ex.: ?string, ?size) — nao e variavel. */
    private static final Pattern BUILTIN = Pattern.compile("\\?\\s*[A-Za-z_][A-Za-z0-9_]*");

    private static final Pattern IDENTIFICADOR = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** Variaveis locais criadas pelo proprio template. */
    private static final Pattern LOCAL_LIST =
            Pattern.compile("<#list\\s+.+?\\s+as\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.DOTALL);
    private static final Pattern LOCAL_ATRIBUICAO =
            Pattern.compile("<#(?:assign|local|global)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern LOCAL_MACRO =
            Pattern.compile("<#macro\\s+([A-Za-z_][A-Za-z0-9_]*)((?:\\s+[A-Za-z_][A-Za-z0-9_]*)*)");

    /** Palavras da propria linguagem, nunca variaveis de dados. */
    private static final Set<String> PALAVRAS_RESERVADAS = Set.of(
            "as", "in", "if", "elseif", "else", "list", "items", "sep", "assign", "local",
            "global", "macro", "nested", "return", "break", "continue", "switch", "case",
            "default", "true", "false", "null", "gt", "gte", "lt", "lte", "and", "or", "not",
            "escape", "noescape", "attempt", "recover", "function", "include", "import",
            "setting", "ftl", "compress", "stop", "outputformat", "autoesc", "noautoesc",
            "t", "rt", "nt", "using");

    /** Builtins que dao acesso a classes/execucao — recusados sempre. */
    private static final Set<String> BUILTINS_PROIBIDOS =
            Set.of("new", "eval", "api", "interpret");

    private static final Pattern SCRIPT = Pattern.compile("<\\s*script", Pattern.CASE_INSENSITIVE);
    private static final Pattern HANDLER_EVENTO =
            Pattern.compile("\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_URI =
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE);
    /** src/href/url() apontando para fora — bloqueado em runtime pelo renderizador. */
    private static final Pattern RECURSO_EXTERNO = Pattern.compile(
            "(?:src|href)\\s*=\\s*[\"']?\\s*(https?:|file:|//)|url\\(\\s*[\"']?\\s*(https?:|file:|//)",
            Pattern.CASE_INSENSITIVE);

    private final Configuration freemarker;

    public TemplateValidator() {
        this.freemarker = new Configuration(Configuration.VERSION_2_3_32);
        this.freemarker.setLocale(new Locale("pt", "BR"));
        this.freemarker.setLogTemplateExceptions(false);
    }

    /**
     * @throws TemplateInvalidoException com a lista completa de problemas, se houver algum.
     */
    public void validar(PdfTemplate template) {
        List<String> erros = new ArrayList<>();

        TemplateChave chave = validarEstrutura(template, erros);
        String html = template.getHtml();

        if (html != null && !html.isBlank()) {
            validarSintaxe(html, erros);
            validarSeguranca(html, erros);
            if (chave != null) {
                validarCampos(html, chave, erros);
            }
        }

        if (!erros.isEmpty()) {
            throw new TemplateInvalidoException(erros);
        }
    }

    /** Campos que a propria linha do banco exige, antes de olhar o conteudo. */
    private TemplateChave validarEstrutura(PdfTemplate t, List<String> erros) {
        if (isBlank(t.getNome())) {
            erros.add("campo obrigatorio ausente: nome");
        }
        if (isBlank(t.getHtml())) {
            erros.add("campo obrigatorio ausente: html");
        }
        if (isBlank(t.getChave())) {
            erros.add("campo obrigatorio ausente: chave");
            return null;
        }
        TemplateChave chave = TemplateChave.de(t.getChave()).orElse(null);
        if (chave == null) {
            erros.add("chave desconhecida: '" + t.getChave()
                    + "' (conhecidas: " + TemplateChave.conhecidas() + ")");
        }
        return chave;
    }

    private void validarSintaxe(String html, List<String> erros) {
        try {
            new Template("validacao", new StringReader(html), freemarker);
        } catch (Exception e) {
            // A mensagem do FreeMarker ja traz linha/coluna do erro.
            erros.add("sintaxe FreeMarker invalida: " + primeiraLinha(e.getMessage()));
        }
    }

    private void validarCampos(String html, TemplateChave chave, List<String> erros) {
        Set<String> referenciadas = variaveisReferenciadas(html);

        Set<String> faltando = new TreeSet<>(chave.obrigatorios());
        faltando.removeAll(referenciadas);
        if (!faltando.isEmpty()) {
            erros.add("template '" + chave.name() + "' nao exibe campo(s) obrigatorio(s): "
                    + String.join(", ", faltando));
        }

        Set<String> desconhecidas = new TreeSet<>(referenciadas);
        desconhecidas.removeAll(chave.permitidos());
        if (!desconhecidas.isEmpty()) {
            erros.add("campo(s) nao reconhecido(s) para '" + chave.name() + "': "
                    + String.join(", ", desconhecidas)
                    + " (disponiveis: " + String.join(", ", new TreeSet<>(chave.permitidos())) + ")");
        }
    }

    private void validarSeguranca(String html, List<String> erros) {
        for (String builtin : BUILTINS_PROIBIDOS) {
            if (Pattern.compile("\\?\\s*" + builtin + "\\b").matcher(html).find()) {
                erros.add("builtin proibido: ?" + builtin
                        + " permite executar codigo a partir do template");
            }
        }
        if (SCRIPT.matcher(html).find()) {
            erros.add("tag <script> nao e permitida: o PDF e estatico e nao executa script");
        }
        if (HANDLER_EVENTO.matcher(html).find()) {
            erros.add("atributo de evento (on...) nao e permitido em template de PDF");
        }
        if (JAVASCRIPT_URI.matcher(html).find()) {
            erros.add("URI javascript: nao e permitida em template de PDF");
        }
        if (RECURSO_EXTERNO.matcher(html).find()) {
            erros.add("recurso externo (http/https/file) referenciado: o renderizador bloqueia "
                    + "carregamento externo (ADR-001); embuta a imagem como data: URI");
        }
    }

    /** Identificadores raiz usados em interpolacoes e diretivas, menos locais e reservadas. */
    Set<String> variaveisReferenciadas(String html) {
        String limpo = COMENTARIO.matcher(html).replaceAll(" ");
        Set<String> locais = variaveisLocais(limpo);
        Set<String> encontradas = new LinkedHashSet<>();

        Matcher expr = EXPRESSAO.matcher(limpo);
        while (expr.find()) {
            String corpo = expr.group(1) != null ? expr.group(1) : expr.group(2);
            if (corpo == null) {
                continue;
            }
            // Literais e builtins sairiam como falsos identificadores.
            corpo = LITERAL.matcher(corpo).replaceAll(" ");
            corpo = BUILTIN.matcher(corpo).replaceAll(" ");

            Matcher id = IDENTIFICADOR.matcher(corpo);
            while (id.find()) {
                String nome = id.group();
                boolean acessoAMembro = id.start() > 0 && corpo.charAt(id.start() - 1) == '.';
                boolean chamada = id.end() < corpo.length() && corpo.charAt(id.end()) == '(';
                if (acessoAMembro || chamada
                        || PALAVRAS_RESERVADAS.contains(nome)
                        || locais.contains(nome)) {
                    continue;
                }
                encontradas.add(nome);
            }
        }
        return encontradas;
    }

    /** Nomes que o proprio template cria (loop vars, assigns, macros e seus parametros). */
    private Set<String> variaveisLocais(String html) {
        Set<String> locais = new LinkedHashSet<>();
        adicionarGrupo1(LOCAL_LIST.matcher(html), locais);
        adicionarGrupo1(LOCAL_ATRIBUICAO.matcher(html), locais);

        Matcher macro = LOCAL_MACRO.matcher(html);
        while (macro.find()) {
            locais.add(macro.group(1));
            String params = macro.group(2) == null ? "" : macro.group(2);
            Matcher param = IDENTIFICADOR.matcher(params);
            while (param.find()) {
                locais.add(param.group());
            }
        }
        return locais;
    }

    private static void adicionarGrupo1(Matcher m, Set<String> destino) {
        while (m.find()) {
            destino.add(m.group(1));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String primeiraLinha(String s) {
        if (s == null) {
            return "erro de parse";
        }
        int quebra = s.indexOf('\n');
        return quebra < 0 ? s : s.substring(0, quebra);
    }
}
