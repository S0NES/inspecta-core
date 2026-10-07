package br.com.vistoria.core.modules.notify;

import br.com.vistoria.core.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotifyTemplateService")
class NotifyTemplateServiceTest {

    @Mock
    private NotifyRepositories.MessageTemplateRepository repo;

    @Mock
    private NotifyRepositories.TemplateUtilidadeRepository utilidadeRepo;

    private NotifyTemplateService service;

    @BeforeEach
    void setUp() {
        service = new NotifyTemplateService(repo, utilidadeRepo);
    }

    private static MessageTemplate versao(UUID uuid, int v) {
        MessageTemplate t = new MessageTemplate();
        t.setUuid(uuid);
        t.setVersao(v);
        t.setCanal("email");
        t.setNome("Template");
        t.setCorpo("Ola {{nome}}");
        return t;
    }

    @Test
    @DisplayName("criar grava versao 1 com uuid novo")
    void criar_versao1() {
        when(repo.save(any(MessageTemplate.class))).thenAnswer(i -> i.getArgument(0));

        MessageTemplate t = service.criar("email", "Boas-vindas", "Assunto", "Ola {{nome}}");

        assertThat(t.getVersao()).isEqualTo(1);
        assertThat(t.getUuid()).isNotNull();
        assertThat(t.getNome()).isEqualTo("Boas-vindas");
    }

    @Test
    @DisplayName("criar recusa canal invalido sem tocar no banco")
    void criar_canalInvalido() {
        assertThatThrownBy(() -> service.criar("carta", "X", null, "corpo"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("criar recusa corpo em branco")
    void criar_corpoBranco() {
        assertThatThrownBy(() -> service.criar("email", "X", null, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("novaVersao incrementa a partir da maior versao, mantendo uuid e canal")
    void novaVersao_incrementa() {
        UUID uuid = UUID.randomUUID();
        when(repo.findFirstByUuidOrderByVersaoDesc(uuid)).thenReturn(Optional.of(versao(uuid, 4)));
        when(repo.save(any(MessageTemplate.class))).thenAnswer(i -> i.getArgument(0));

        MessageTemplate t = service.novaVersao(uuid, "Novo nome", "Assunto", "Novo corpo {{nome}}");

        assertThat(t.getVersao()).isEqualTo(5);
        assertThat(t.getUuid()).isEqualTo(uuid);
        assertThat(t.getCanal()).isEqualTo("email");
    }

    @Test
    @DisplayName("novaVersao de um uuid inexistente e 404")
    void novaVersao_inexistente() {
        UUID uuid = UUID.randomUUID();
        when(repo.findFirstByUuidOrderByVersaoDesc(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.novaVersao(uuid, "n", null, "c"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("desativar marca todas as versoes como inativas")
    void desativar_todasAsVersoes() {
        UUID uuid = UUID.randomUUID();
        MessageTemplate v1 = versao(uuid, 1);
        MessageTemplate v2 = versao(uuid, 2);
        when(repo.findByUuidOrderByVersaoDesc(uuid)).thenReturn(List.of(v2, v1));

        service.desativar(uuid);

        assertThat(v1.isAtivo()).isFalse();
        assertThat(v2.isAtivo()).isFalse();
    }

    @Test
    @DisplayName("desativar de um uuid inexistente e 404")
    void desativar_inexistente() {
        UUID uuid = UUID.randomUUID();
        when(repo.findByUuidOrderByVersaoDesc(uuid)).thenReturn(List.of());

        assertThatThrownBy(() -> service.desativar(uuid)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("listar repassa o filtro de canal (blank vira null)")
    void listar_filtroCanal() {
        service.listar("  ");
        ArgumentCaptor<String> canal = ArgumentCaptor.forClass(String.class);
        verify(repo).listarUltimaVersao(canal.capture());
        assertThat(canal.getValue()).isNull();
    }

    @Test
    @DisplayName("definirUtilidade grava a ligacao quando o alvo e um template de email ativo")
    void definirUtilidade_ok() {
        UUID alvo = UUID.randomUUID();
        when(repo.findFirstByUuidAndAtivoTrueOrderByVersaoDesc(alvo)).thenReturn(Optional.of(versao(alvo, 3)));
        when(utilidadeRepo.findById("password_reset")).thenReturn(Optional.empty());

        service.definirUtilidade("password_reset", alvo, UUID.fromString("00000000-0000-0000-0000-000000000042"));

        ArgumentCaptor<TemplateUtilidadeEntity> ent = ArgumentCaptor.forClass(TemplateUtilidadeEntity.class);
        verify(utilidadeRepo).save(ent.capture());
        assertThat(ent.getValue().getUtilidade()).isEqualTo("password_reset");
        assertThat(ent.getValue().getTemplateId()).isEqualTo(alvo);
        assertThat(ent.getValue().getAtualizadoPor()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000042"));
    }

    @Test
    @DisplayName("definirUtilidade recusa utilidade desconhecida")
    void definirUtilidade_utilidadeDesconhecida() {
        assertThatThrownBy(() -> service.definirUtilidade("nao_existe", UUID.randomUUID(), UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(utilidadeRepo, never()).save(any());
    }

    @Test
    @DisplayName("definirUtilidade recusa template que nao e do canal email")
    void definirUtilidade_canalErrado() {
        UUID alvo = UUID.randomUUID();
        MessageTemplate sms = versao(alvo, 1);
        sms.setCanal("sms");
        when(repo.findFirstByUuidAndAtivoTrueOrderByVersaoDesc(alvo)).thenReturn(Optional.of(sms));

        assertThatThrownBy(() -> service.definirUtilidade("password_reset", alvo, UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(utilidadeRepo, never()).save(any());
    }

    @Test
    @DisplayName("definirUtilidade e 404 quando o template alvo nao existe/esta inativo")
    void definirUtilidade_alvoInexistente() {
        UUID alvo = UUID.randomUUID();
        when(repo.findFirstByUuidAndAtivoTrueOrderByVersaoDesc(alvo)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.definirUtilidade("user_invited", alvo, UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("listarUtilidades cai no uuid padrao quando nao ha linha configurada")
    void listarUtilidades_default() {
        when(utilidadeRepo.findAll()).thenReturn(List.of());
        when(repo.findFirstByUuidAndAtivoTrueOrderByVersaoDesc(any())).thenReturn(Optional.empty());

        List<NotifyTemplateService.UtilidadeView> views = service.listarUtilidades();

        assertThat(views).hasSize(TemplateUtilidade.values().length);
        NotifyTemplateService.UtilidadeView reset = views.stream()
                .filter(v -> v.utilidade().equals("password_reset")).findFirst().orElseThrow();
        assertThat(reset.templateId()).isEqualTo(UUID.fromString(TemplateUtilidade.password_reset.uuidPadrao()));
        assertThat(reset.variaveis()).containsExactly("codigo");
    }
}
