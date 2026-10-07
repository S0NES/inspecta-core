package br.com.vistoria.core.modules.pdftemplate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PdfTemplateService")
class PdfTemplateServiceTest {

    private static final String HTML_VALIDO = """
            <html><body>
              <h1>Laudo v${versao}</h1>
              <p>${solicitacaoServicoId} - ${emitidoEm}</p>
              <#list ambientes as amb><p>${amb.nome}</p></#list>
            </body></html>""";

    @Mock
    private PdfTemplateRepository repository;

    private PdfTemplateService service;

    @BeforeEach
    void setUp() {
        // Validator real, nao mock: o ponto do teste e que salvar nao aceita template invalido.
        service = new PdfTemplateService(repository, new TemplateValidator());
    }

    private static PdfTemplate novo(UUID imobiliariaId) {
        PdfTemplate t = new PdfTemplate();
        t.setChave("laudo");
        t.setNome("Laudo padrao");
        t.setHtml(HTML_VALIDO);
        t.setImobiliariaId(imobiliariaId);
        return t;
    }

    @Nested
    @DisplayName("salvar")
    class Salvar {

        @Test
        @DisplayName("recusa template invalido sem tocar no banco")
        void salvar_invalido_naoPersiste() {
            PdfTemplate ruim = novo(UUID.fromString("00000000-0000-0000-0000-000000000007"));
            ruim.setHtml("<html><body><p>${versao}</p></body></html>"); // faltam obrigatorios

            assertThatThrownBy(() -> service.salvar(ruim))
                    .isInstanceOf(TemplateInvalidoException.class);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("primeira versao de uma imobiliaria e 1")
        void salvar_primeiroTemplate_versao1() {
            when(repository.findFirstByChaveAndImobiliariaIdOrderByVersaoDesc("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007")))
                    .thenReturn(Optional.empty());
            when(repository.save(any(PdfTemplate.class))).thenAnswer(i -> i.getArgument(0));

            PdfTemplate salvo = service.salvar(novo(UUID.fromString("00000000-0000-0000-0000-000000000007")));

            assertThat(salvo.getVersao()).isEqualTo(1);
        }

        @Test
        @DisplayName("nova gravacao vira versao+1, nunca sobrescreve")
        void salvar_existente_incrementaVersao() {
            PdfTemplate anterior = novo(UUID.fromString("00000000-0000-0000-0000-000000000007"));
            anterior.setId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
            anterior.setVersao(4);
            when(repository.findFirstByChaveAndImobiliariaIdOrderByVersaoDesc("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007")))
                    .thenReturn(Optional.of(anterior));
            when(repository.save(any(PdfTemplate.class))).thenAnswer(i -> i.getArgument(0));

            PdfTemplate salvo = service.salvar(novo(UUID.fromString("00000000-0000-0000-0000-000000000007")));

            assertThat(salvo.getVersao()).isEqualTo(5);
        }

        @Test
        @DisplayName("ignora id enviado pelo chamador (evita update disfarcado)")
        void salvar_comId_criaNovaLinha() {
            // Um laudo assinado precisa continuar apontando para o template que o gerou.
            PdfTemplate comId = novo(UUID.fromString("00000000-0000-0000-0000-000000000007"));
            comId.setId(UUID.fromString("00000000-0000-0000-0000-000000000123"));
            when(repository.findFirstByChaveAndImobiliariaIdOrderByVersaoDesc("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007")))
                    .thenReturn(Optional.empty());
            when(repository.save(any(PdfTemplate.class))).thenAnswer(i -> i.getArgument(0));

            PdfTemplate salvo = service.salvar(comId);

            assertThat(salvo.getId()).isNull();
        }

        @Test
        @DisplayName("template global usa a consulta de versao global")
        void salvar_global_usaConsultaGlobal() {
            when(repository.findFirstByChaveAndImobiliariaIdIsNullOrderByVersaoDesc("laudo"))
                    .thenReturn(Optional.empty());
            when(repository.save(any(PdfTemplate.class))).thenAnswer(i -> i.getArgument(0));

            PdfTemplate salvo = service.salvar(novo(null));

            assertThat(salvo.isGlobal()).isTrue();
            verify(repository, never())
                    .findFirstByChaveAndImobiliariaIdOrderByVersaoDesc(anyString(), any(UUID.class));
        }
    }

    @Nested
    @DisplayName("resolver")
    class Resolver {

        @Test
        @DisplayName("prefere o template da imobiliaria")
        void resolver_imobiliariaTemProprio_usaOProprio() {
            PdfTemplate proprio = novo(UUID.fromString("00000000-0000-0000-0000-000000000007"));
            when(repository.findFirstByChaveAndImobiliariaIdAndAtivoTrueOrderByVersaoDesc("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007")))
                    .thenReturn(Optional.of(proprio));

            assertThat(service.resolver("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007"))).contains(proprio);
            verify(repository, never())
                    .findFirstByChaveAndImobiliariaIdIsNullAndAtivoTrueOrderByVersaoDesc(anyString());
        }

        @Test
        @DisplayName("cai no global quando a imobiliaria nao tem o proprio")
        void resolver_semProprio_caiNoGlobal() {
            PdfTemplate global = novo(null);
            when(repository.findFirstByChaveAndImobiliariaIdAndAtivoTrueOrderByVersaoDesc("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007")))
                    .thenReturn(Optional.empty());
            when(repository.findFirstByChaveAndImobiliariaIdIsNullAndAtivoTrueOrderByVersaoDesc("laudo"))
                    .thenReturn(Optional.of(global));

            assertThat(service.resolver("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007"))).contains(global);
        }

        @Test
        @DisplayName("vazio quando nao ha template nenhum (chamador usa o embutido)")
        void resolver_semNada_vazio() {
            when(repository.findFirstByChaveAndImobiliariaIdAndAtivoTrueOrderByVersaoDesc("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007")))
                    .thenReturn(Optional.empty());
            when(repository.findFirstByChaveAndImobiliariaIdIsNullAndAtivoTrueOrderByVersaoDesc("laudo"))
                    .thenReturn(Optional.empty());

            assertThat(service.resolver("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007"))).isEmpty();
        }

        @Test
        @DisplayName("tenant nulo consulta direto o global")
        void resolver_semImobiliaria_vaiDiretoAoGlobal() {
            PdfTemplate global = novo(null);
            when(repository.findFirstByChaveAndImobiliariaIdIsNullAndAtivoTrueOrderByVersaoDesc("laudo"))
                    .thenReturn(Optional.of(global));

            assertThat(service.resolver("laudo", null)).contains(global);
        }
    }

    @Test
    @DisplayName("desativar marca inativo para cair no fallback")
    void desativar_marcaInativo() {
        PdfTemplate t = novo(UUID.fromString("00000000-0000-0000-0000-000000000007"));
        t.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000005"))).thenReturn(Optional.of(t));

        service.desativar(UUID.fromString("00000000-0000-0000-0000-000000000005"));

        assertThat(t.isAtivo()).isFalse();
    }

    @Nested
    @DisplayName("resolverParaImobiliaria")
    class ResolverParaImobiliaria {

        @Test
        @DisplayName("vinculo explicito valido vence a resolucao por chave")
        void explicitoValido_vence() {
            PdfTemplate vinculado = novo(UUID.fromString("00000000-0000-0000-0000-000000000007"));
            vinculado.setId(UUID.fromString("00000000-0000-0000-0000-000000000042"));
            when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000042"))).thenReturn(Optional.of(vinculado));

            assertThat(service.resolverParaImobiliaria("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007"), UUID.fromString("00000000-0000-0000-0000-000000000042"))).contains(vinculado);
            verify(repository, never())
                    .findFirstByChaveAndImobiliariaIdAndAtivoTrueOrderByVersaoDesc(anyString(), any(UUID.class));
        }

        @Test
        @DisplayName("vinculo para template inativo e ignorado: cai na resolucao normal")
        void explicitoInativo_caiNoFallback() {
            PdfTemplate inativo = novo(UUID.fromString("00000000-0000-0000-0000-000000000007"));
            inativo.setId(UUID.fromString("00000000-0000-0000-0000-000000000042"));
            inativo.setAtivo(false);
            PdfTemplate daImob = novo(UUID.fromString("00000000-0000-0000-0000-000000000007"));
            when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000042"))).thenReturn(Optional.of(inativo));
            when(repository.findFirstByChaveAndImobiliariaIdAndAtivoTrueOrderByVersaoDesc("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007")))
                    .thenReturn(Optional.of(daImob));

            assertThat(service.resolverParaImobiliaria("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007"), UUID.fromString("00000000-0000-0000-0000-000000000042"))).contains(daImob);
        }

        @Test
        @DisplayName("vinculo de outra chave e ignorado")
        void explicitoOutraChave_caiNoFallback() {
            PdfTemplate outraChave = novo(UUID.fromString("00000000-0000-0000-0000-000000000007"));
            outraChave.setId(UUID.fromString("00000000-0000-0000-0000-000000000042"));
            outraChave.setChave("recibo");
            when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000042"))).thenReturn(Optional.of(outraChave));
            when(repository.findFirstByChaveAndImobiliariaIdAndAtivoTrueOrderByVersaoDesc("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007")))
                    .thenReturn(Optional.empty());
            when(repository.findFirstByChaveAndImobiliariaIdIsNullAndAtivoTrueOrderByVersaoDesc("laudo"))
                    .thenReturn(Optional.empty());

            assertThat(service.resolverParaImobiliaria("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007"), UUID.fromString("00000000-0000-0000-0000-000000000042"))).isEmpty();
        }

        @Test
        @DisplayName("sem vinculo (null) usa a resolucao por chave")
        void semVinculo_resolucaoNormal() {
            PdfTemplate global = novo(null);
            when(repository.findFirstByChaveAndImobiliariaIdAndAtivoTrueOrderByVersaoDesc("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007")))
                    .thenReturn(Optional.empty());
            when(repository.findFirstByChaveAndImobiliariaIdIsNullAndAtivoTrueOrderByVersaoDesc("laudo"))
                    .thenReturn(Optional.of(global));

            assertThat(service.resolverParaImobiliaria("laudo", UUID.fromString("00000000-0000-0000-0000-000000000007"), null)).contains(global);
        }
    }
}
