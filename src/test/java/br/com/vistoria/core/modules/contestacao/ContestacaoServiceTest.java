package br.com.vistoria.core.modules.contestacao;

import br.com.vistoria.core.modules.laudo.Laudo;
import br.com.vistoria.core.modules.laudo.LaudoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContestacaoService")
class ContestacaoServiceTest {

    @Mock
    private ContestacaoService.ContestacaoRepository contestacoes;

    @Mock
    private LaudoService laudoService;

    private ContestacaoService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new ContestacaoService(contestacoes, laudoService, mapper);
    }

    @Test
    @DisplayName("criar creates contestacao when node exists")
    void criar_createsContestacao() throws Exception {
        UUID nodeUuid = UUID.randomUUID();
        String json = "{\"uuid\":\"" + nodeUuid + "\",\"valor\":100}";

        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        laudo.setConteudoJson(json);

        Contestacao saved = new Contestacao();
        saved.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        saved.setLaudoId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        saved.setNodeUuid(nodeUuid);
        saved.setAutorId(UUID.fromString("00000000-0000-0000-0000-000000000042"));
        saved.setTexto("Disputo este item");

        when(laudoService.get(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(laudo);
        when(contestacoes.save(any(Contestacao.class))).thenReturn(saved);

        Contestacao result = service.criar(UUID.fromString("00000000-0000-0000-0000-000000000001"), nodeUuid, UUID.fromString("00000000-0000-0000-0000-000000000042"), "Disputo este item");

        assertThat(result.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(result.getNodeUuid()).isEqualTo(nodeUuid);
        assertThat(result.getAutorId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000042"));
        assertThat(result.getTexto()).isEqualTo("Disputo este item");
    }

    @Test
    @DisplayName("criar without nodeUuid contests the whole laudo")
    void criar_semNode_contestaLaudoInteiro() {
        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        laudo.setConteudoJson("{}");

        when(laudoService.get(laudo.getId())).thenReturn(laudo);
        when(contestacoes.save(any(Contestacao.class))).thenAnswer(inv -> inv.getArgument(0));

        Contestacao result = service.criar(laudo.getId(), null,
                UUID.fromString("00000000-0000-0000-0000-000000000042"), "A pintura já estava danificada");

        assertThat(result.getNodeUuid()).isEqualTo(ContestacaoService.LAUDO_INTEIRO);
        assertThat(result.getTexto()).isEqualTo("A pintura já estava danificada");
    }

    @Test
    @DisplayName("criar throws when node uuid does not exist in laudo")
    void criar_throwsWhenNodeNotFound() {
        UUID nodeUuid = UUID.randomUUID();
        String json = "{\"uuid\":\"00000000-0000-0000-0000-000000000000\",\"valor\":100}";

        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        laudo.setConteudoJson(json);

        when(laudoService.get(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(laudo);

        assertThatThrownBy(() -> service.criar(UUID.fromString("00000000-0000-0000-0000-000000000001"), nodeUuid, UUID.fromString("00000000-0000-0000-0000-000000000042"), "text"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("node_uuid nao existe");
    }

    @Test
    @DisplayName("resolver accepts contestacao and triggers new version")
    void resolver_accepts() {
        UUID nodeUuid = UUID.randomUUID();

        Contestacao contestacao = new Contestacao();
        contestacao.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        contestacao.setLaudoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        contestacao.setNodeUuid(nodeUuid);
        contestacao.setStatus(Contestacao.Status.aberta);

        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        laudo.setStatus(Laudo.Status.assinado);

        when(contestacoes.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(contestacao));
        when(laudoService.get(UUID.fromString("00000000-0000-0000-0000-000000000100"))).thenReturn(laudo);

        service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000001"), true, "Aceito, criando nova versao");

        assertThat(contestacao.getStatus()).isEqualTo(Contestacao.Status.aceita);
        assertThat(contestacao.getResolucao()).isEqualTo("Aceito, criando nova versao");
    }

    @Test
    @DisplayName("resolver rejects contestacao")
    void resolver_rejects() {
        Contestacao contestacao = new Contestacao();
        contestacao.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        contestacao.setLaudoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        contestacao.setStatus(Contestacao.Status.aberta);

        when(contestacoes.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(contestacao));

        service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000001"), false, "Rejeitado por motivo X");

        assertThat(contestacao.getStatus()).isEqualTo(Contestacao.Status.rejeitada);
        assertThat(contestacao.getResolucao()).isEqualTo("Rejeitado por motivo X");
    }

    @Test
    @DisplayName("resolver throws when contestacao not found")
    void resolver_throwsNotFound() {
        when(contestacoes.findById(UUID.fromString("00000000-0000-0000-0000-000000000999"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolver(UUID.fromString("00000000-0000-0000-0000-000000000999"), true, "text"))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    @DisplayName("criar handles nested JSON with array of nodes")
    void criar_handlesNestedArray() throws Exception {
        UUID nodeUuid = UUID.randomUUID();
        String json = "{\"children\":[{\"uuid\":\"" + nodeUuid + "\",\"valor\":50}]}";

        Laudo laudo = new Laudo();
        laudo.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        laudo.setConteudoJson(json);

        Contestacao saved = new Contestacao();
        saved.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        saved.setLaudoId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        when(laudoService.get(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(laudo);
        when(contestacoes.save(any(Contestacao.class))).thenReturn(saved);

        Contestacao result = service.criar(UUID.fromString("00000000-0000-0000-0000-000000000001"), nodeUuid, UUID.fromString("00000000-0000-0000-0000-000000000042"), "Nested node contestacao");

        assertThat(result.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }
}
