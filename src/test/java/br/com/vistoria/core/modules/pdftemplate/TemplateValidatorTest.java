package br.com.vistoria.core.modules.pdftemplate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TemplateValidator (checagem no save)")
class TemplateValidatorTest {

    private final TemplateValidator validator = new TemplateValidator();

    /** Template minimo que satisfaz o contrato de 'laudo'. */
    private static final String HTML_VALIDO = """
            <html><body>
              <h1>Laudo v${versao}</h1>
              <p>Servico ${solicitacaoServicoId} emitido em ${emitidoEm}</p>
              <#list ambientes as amb>
                <h2>${amb.nome}</h2>
                <#list amb.itens as item>
                  <p>${item.descricao}: ${item.estado}</p>
                </#list>
              </#list>
            </body></html>""";

    private static PdfTemplate template(String html) {
        PdfTemplate t = new PdfTemplate();
        t.setChave("laudo");
        t.setNome("Laudo padrao");
        t.setHtml(html);
        return t;
    }

    private static java.util.List<String> errosDe(PdfTemplate t, TemplateValidator v) {
        try {
            v.validar(t);
            throw new AssertionError("esperava TemplateInvalidoException");
        } catch (TemplateInvalidoException e) {
            return e.getErros();
        }
    }

    @Test
    @DisplayName("aceita um template completo e bem formado")
    void validar_templateCompleto_passa() {
        assertThatCode(() -> validator.validar(template(HTML_VALIDO))).doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("campos obrigatorios")
    class CamposObrigatorios {

        @Test
        @DisplayName("recusa template que omite um campo obrigatorio")
        void validar_semAmbientes_recusa() {
            String semAmbientes = """
                    <html><body>
                      <h1>Laudo v${versao}</h1>
                      <p>${solicitacaoServicoId} - ${emitidoEm}</p>
                    </body></html>""";

            assertThatThrownBy(() -> validator.validar(template(semAmbientes)))
                    .isInstanceOf(TemplateInvalidoException.class)
                    .hasMessageContaining("obrigatorio")
                    .hasMessageContaining("ambientes");
        }

        @Test
        @DisplayName("lista todos os obrigatorios ausentes de uma vez")
        void validar_varriosAusentes_listaTodos() {
            // Quem edita o template quer corrigir tudo numa passada, nao um erro por vez.
            var erros = errosDe(template("<html><body><p>${versao}</p></body></html>"), validator);

            assertThat(erros).anySatisfy(e -> assertThat(e)
                    .contains("ambientes")
                    .contains("emitidoEm")
                    .contains("solicitacaoServicoId"));
        }

        @Test
        @DisplayName("campo obrigatorio dentro de #if conta como exibido")
        void validar_obrigatorioEmCondicional_aceita() {
            String comIf = """
                    <html><body>
                      <h1>v${versao}</h1><p>${solicitacaoServicoId} ${emitidoEm}</p>
                      <#if ambientes?size gt 0>
                        <#list ambientes as amb><p>${amb.nome}</p></#list>
                      </#if>
                    </body></html>""";

            assertThatCode(() -> validator.validar(template(comIf))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("campos desconhecidos")
    class CamposDesconhecidos {

        @Test
        @DisplayName("recusa erro de digitacao no nome do campo")
        void validar_campoInexistente_recusa() {
            // 'ambiente' no singular renderizaria vazio em silencio no consumer.
            String comTypo = HTML_VALIDO.replace("${amb.nome}", "${ambiente}");

            assertThatThrownBy(() -> validator.validar(template(comTypo)))
                    .isInstanceOf(TemplateInvalidoException.class)
                    .hasMessageContaining("nao reconhecido")
                    .hasMessageContaining("ambiente");
        }

        @Test
        @DisplayName("aceita campos opcionais do catalogo")
        void validar_camposOpcionais_aceita() {
            String comOpcionais = HTML_VALIDO.replace("<h1>",
                    "<p>${imobiliariaNome}</p><p>${rodape}</p><h1>");

            assertThatCode(() -> validator.validar(template(comOpcionais)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("variavel local de #list nao e tratada como campo desconhecido")
        void validar_variavelLocal_naoAcusa() {
            assertThatCode(() -> validator.validar(template(HTML_VALIDO)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("variavel criada por #assign nao e tratada como campo desconhecido")
        void validar_assign_naoAcusa() {
            String comAssign = HTML_VALIDO.replace("<h1>",
                    "<#assign titulo = \"Laudo de Vistoria\">" + "<p>${titulo}</p><h1>");

            assertThatCode(() -> validator.validar(template(comAssign)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("builtin nao e confundido com campo")
        void validar_builtin_naoAcusa() {
            String comBuiltin = HTML_VALIDO.replace("${versao}", "${versao?string}");

            assertThatCode(() -> validator.validar(template(comBuiltin)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("texto dentro de string literal nao e confundido com campo")
        void validar_literal_naoAcusa() {
            String comLiteral = HTML_VALIDO.replace("<h1>",
                    "<#if versao gt 0><p>${\"texto_qualquer\"}</p></#if><h1>");

            assertThatCode(() -> validator.validar(template(comLiteral)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("campos estruturais da propria linha")
    class Estrutura {

        @Test
        @DisplayName("recusa nome em branco")
        void validar_semNome_recusa() {
            PdfTemplate t = template(HTML_VALIDO);
            t.setNome("  ");

            assertThat(errosDe(t, validator)).anySatisfy(e -> assertThat(e).contains("nome"));
        }

        @Test
        @DisplayName("recusa html em branco")
        void validar_semHtml_recusa() {
            PdfTemplate t = template("");

            assertThat(errosDe(t, validator)).anySatisfy(e -> assertThat(e).contains("html"));
        }

        @Test
        @DisplayName("recusa chave em branco")
        void validar_semChave_recusa() {
            PdfTemplate t = template(HTML_VALIDO);
            t.setChave(null);

            assertThat(errosDe(t, validator)).anySatisfy(e -> assertThat(e).contains("chave"));
        }

        @Test
        @DisplayName("recusa chave fora do catalogo")
        void validar_chaveDesconhecida_recusa() {
            PdfTemplate t = template(HTML_VALIDO);
            t.setChave("boleto");

            assertThatThrownBy(() -> validator.validar(t))
                    .isInstanceOf(TemplateInvalidoException.class)
                    .hasMessageContaining("chave desconhecida")
                    .hasMessageContaining("boleto");
        }
    }

    @Nested
    @DisplayName("sintaxe FreeMarker")
    class Sintaxe {

        @Test
        @DisplayName("recusa diretiva nao fechada")
        void validar_listSemFechar_recusa() {
            String quebrado = """
                    <html><body>
                      <p>${versao} ${solicitacaoServicoId} ${emitidoEm}</p>
                      <#list ambientes as amb><p>${amb.nome}</p>
                    </body></html>""";

            assertThatThrownBy(() -> validator.validar(template(quebrado)))
                    .isInstanceOf(TemplateInvalidoException.class)
                    .hasMessageContaining("sintaxe FreeMarker");
        }

        @Test
        @DisplayName("recusa interpolacao nao fechada")
        void validar_interpolacaoAberta_recusa() {
            String quebrado = HTML_VALIDO.replace("${versao}", "${versao");

            assertThatThrownBy(() -> validator.validar(template(quebrado)))
                    .isInstanceOf(TemplateInvalidoException.class);
        }
    }

    @Nested
    @DisplayName("seguranca")
    class Seguranca {

        @Test
        @DisplayName("recusa ?new (instancia classes arbitrarias)")
        void validar_builtinNew_recusa() {
            String malicioso = HTML_VALIDO.replace("<h1>",
                    "${\"freemarker.template.utility.Execute\"?new()(\"calc\")}<h1>");

            assertThatThrownBy(() -> validator.validar(template(malicioso)))
                    .isInstanceOf(TemplateInvalidoException.class)
                    .hasMessageContaining("?new");
        }

        @Test
        @DisplayName("recusa ?eval")
        void validar_builtinEval_recusa() {
            String malicioso = HTML_VALIDO.replace("<h1>", "${versao?eval}<h1>");

            assertThatThrownBy(() -> validator.validar(template(malicioso)))
                    .isInstanceOf(TemplateInvalidoException.class)
                    .hasMessageContaining("?eval");
        }

        @Test
        @DisplayName("recusa tag script")
        void validar_script_recusa() {
            String comScript = HTML_VALIDO.replace("<h1>", "<script>alert(1)</script><h1>");

            assertThatThrownBy(() -> validator.validar(template(comScript)))
                    .isInstanceOf(TemplateInvalidoException.class)
                    .hasMessageContaining("script");
        }

        @Test
        @DisplayName("recusa atributo de evento")
        void validar_handlerEvento_recusa() {
            String comHandler = HTML_VALIDO.replace("<body>", "<body onload=\"x()\">");

            assertThatThrownBy(() -> validator.validar(template(comHandler)))
                    .isInstanceOf(TemplateInvalidoException.class)
                    .hasMessageContaining("evento");
        }

        @Test
        @DisplayName("recusa recurso externo (mesma regra do renderizador)")
        void validar_imagemRemota_recusa() {
            String comRemoto = HTML_VALIDO.replace("<h1>",
                    "<img src=\"https://cdn.exemplo.com/logo.png\"/><h1>");

            assertThatThrownBy(() -> validator.validar(template(comRemoto)))
                    .isInstanceOf(TemplateInvalidoException.class)
                    .hasMessageContaining("recurso externo");
        }

        @Test
        @DisplayName("recusa file: (leitura local)")
        void validar_fileUri_recusa() {
            String comFile = HTML_VALIDO.replace("<h1>", "<img src=\"file:///etc/passwd\"/><h1>");

            assertThatThrownBy(() -> validator.validar(template(comFile)))
                    .isInstanceOf(TemplateInvalidoException.class)
                    .hasMessageContaining("recurso externo");
        }

        @Test
        @DisplayName("aceita logo embutido como data: URI")
        void validar_dataUri_aceita() {
            // O caminho suportado para marca da imobiliaria.
            String comLogo = HTML_VALIDO.replace("<h1>",
                    "<img src=\"data:image/png;base64,iVBORw0KGgo=\"/><h1>");

            assertThatCode(() -> validator.validar(template(comLogo))).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("acumula problemas de familias diferentes numa unica excecao")
    void validar_multiplosProblemas_acumula() {
        PdfTemplate t = template("<html><body><script>x</script><p>${versao}</p></body></html>");
        t.setNome("");

        var erros = errosDe(t, validator);

        assertThat(erros).hasSizeGreaterThanOrEqualTo(3);
        assertThat(String.join(" | ", erros))
                .contains("nome")
                .contains("script")
                .contains("obrigatorio");
    }
}
