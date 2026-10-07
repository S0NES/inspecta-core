package br.com.vistoria.core.modules.cadastro;

import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ServicoRepository;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ServicoTipoRepository;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.TipoServicoRepository;
import br.com.vistoria.core.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ServicoService")
class ServicoServiceTest {

    @Mock private ServicoRepository servicos;
    @Mock private ServicoTipoRepository vinculos;
    @Mock private TipoServicoRepository tiposServico;

    private ServicoService service;

    @BeforeEach
    void setUp() {
        service = new ServicoService(servicos, vinculos, tiposServico);
    }

    /** UUID determinístico por número pequeno — legibilidade nos fixtures deste teste. */
    private static UUID uid(long n) {
        return new UUID(0L, n);
    }

    private static TipoServico tipo(UUID id, String nome) {
        TipoServico t = new TipoServico();
        t.setId(id);
        t.setNome(nome);
        return t;
    }

    private static Servico servico(UUID id, String nome) {
        Servico s = new Servico();
        s.setId(id);
        s.setNome(nome);
        return s;
    }

    @Test
    @DisplayName("criar persiste o serviço e vincula os tipos informados")
    void criar_ok() {
        when(servicos.findByNomeIgnoreCase("Elegance")).thenReturn(Optional.empty());
        when(servicos.save(any(Servico.class))).thenAnswer(inv -> {
            Servico s = inv.getArgument(0);
            s.setId(uid(7));
            return s;
        });
        when(tiposServico.findAllById(anyCollection()))
                .thenReturn(List.of(tipo(uid(1), "Vistoria de entrada"), tipo(uid(2), "Vistoria de saída")));
        when(servicos.findAllByOrderByNomeAsc()).thenReturn(List.of(servico(uid(7), "Elegance")));
        when(tiposServico.findAll())
                .thenReturn(List.of(tipo(uid(1), "Vistoria de entrada"), tipo(uid(2), "Vistoria de saída")));
        ServicoTipo v1 = new ServicoTipo(); v1.setServicoId(uid(7)); v1.setTipoServicoId(uid(1));
        ServicoTipo v2 = new ServicoTipo(); v2.setServicoId(uid(7)); v2.setTipoServicoId(uid(2));
        when(vinculos.findByServicoIdIn(anyCollection())).thenReturn(List.of(v1, v2));

        ServicoService.ServicoView view = service.criar("  Elegance ", List.of(uid(1), uid(2), uid(1)));

        assertThat(view.id()).isEqualTo(uid(7));
        assertThat(view.nome()).isEqualTo("Elegance");
        assertThat(view.tipos()).extracting(ServicoService.TipoRef::id).containsExactly(uid(1), uid(2));
        verify(vinculos, org.mockito.Mockito.never()).deleteAll(org.mockito.ArgumentMatchers.anyList());
        verify(vinculos, org.mockito.Mockito.times(2)).save(any(ServicoTipo.class));
    }

    @Test
    @DisplayName("criar rejeita nome em branco")
    void criar_nomeVazio() {
        assertThatThrownBy(() -> service.criar("   ", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nome");
    }

    @Test
    @DisplayName("criar rejeita nome já existente")
    void criar_nomeDuplicado() {
        when(servicos.findByNomeIgnoreCase("Elegance")).thenReturn(Optional.of(servico(uid(1), "Elegance")));
        assertThatThrownBy(() -> service.criar("Elegance", List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("criar falha se um tipo de serviço não existe")
    void criar_tipoInexistente() {
        when(servicos.findByNomeIgnoreCase(any())).thenReturn(Optional.empty());
        when(servicos.save(any(Servico.class))).thenAnswer(inv -> { Servico s = inv.getArgument(0); s.setId(uid(5)); return s; });
        when(tiposServico.findAllById(anyCollection())).thenReturn(List.of());
        assertThatThrownBy(() -> service.criar("Novo", List.of(uid(99))))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(uid(99).toString());
    }

    @Test
    @DisplayName("atualizar troca nome, ativo e o conjunto de tipos")
    void atualizar_ok() {
        when(servicos.findById(uid(3))).thenReturn(Optional.of(servico(uid(3), "Antigo")));
        when(servicos.findByNomeIgnoreCase("Novo")).thenReturn(Optional.empty());
        when(tiposServico.findAllById(anyCollection())).thenReturn(List.of(tipo(uid(1), "Entrada")));
        when(servicos.findAllByOrderByNomeAsc()).thenReturn(List.of(servico(uid(3), "Novo")));
        when(tiposServico.findAll()).thenReturn(List.of(tipo(uid(1), "Entrada")));
        ServicoTipo v = new ServicoTipo(); v.setServicoId(uid(3)); v.setTipoServicoId(uid(1));
        when(vinculos.findByServicoIdIn(anyCollection())).thenReturn(List.of(v));

        ServicoService.ServicoView view = service.atualizar(uid(3), "Novo", false, List.of(uid(1)));

        assertThat(view.nome()).isEqualTo("Novo");
        verify(vinculos).save(any(ServicoTipo.class));
    }

    @Test
    @DisplayName("atualizar reeditando com os mesmos tipos não recria vínculos (sem 409)")
    void atualizar_mesmosTipos_naoRecria() {
        when(servicos.findById(uid(3))).thenReturn(Optional.of(servico(uid(3), "Elegance")));
        when(servicos.findByNomeIgnoreCase("Elegance")).thenReturn(Optional.of(servico(uid(3), "Elegance")));
        when(tiposServico.findAllById(anyCollection()))
                .thenReturn(List.of(tipo(uid(1), "Entrada"), tipo(uid(2), "Saída")));
        ServicoTipo v1 = new ServicoTipo(); v1.setServicoId(uid(3)); v1.setTipoServicoId(uid(1)); v1.setPlenoTipVisCodigo(42);
        ServicoTipo v2 = new ServicoTipo(); v2.setServicoId(uid(3)); v2.setTipoServicoId(uid(2));
        when(vinculos.findByServicoId(uid(3))).thenReturn(List.of(v1, v2));
        when(servicos.findAllByOrderByNomeAsc()).thenReturn(List.of(servico(uid(3), "Elegance")));
        when(tiposServico.findAll()).thenReturn(List.of(tipo(uid(1), "Entrada"), tipo(uid(2), "Saída")));
        when(vinculos.findByServicoIdIn(anyCollection())).thenReturn(List.of(v1, v2));

        service.atualizar(uid(3), "Elegance", null, List.of(uid(1), uid(2)));

        verify(vinculos, org.mockito.Mockito.never()).save(any(ServicoTipo.class));
        verify(vinculos, org.mockito.Mockito.never()).deleteAll(org.mockito.ArgumentMatchers.anyList());
        assertThat(v1.getPlenoTipVisCodigo()).isEqualTo(42); // preserva o código Pleno
    }

    @Test
    @DisplayName("atualizar rejeita nome usado por outro serviço")
    void atualizar_nomeConflito() {
        when(servicos.findById(uid(3))).thenReturn(Optional.of(servico(uid(3), "Antigo")));
        when(servicos.findByNomeIgnoreCase("Elegance")).thenReturn(Optional.of(servico(uid(9), "Elegance")));
        assertThatThrownBy(() -> service.atualizar(uid(3), "Elegance", null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("atualizar 404 quando o serviço não existe")
    void atualizar_naoEncontrado() {
        when(servicos.findById(uid(404))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.atualizar(uid(404), "x", null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("excluir remove vínculos e o serviço")
    void excluir_ok() {
        when(servicos.existsById(uid(2))).thenReturn(true);
        service.excluir(uid(2));
        verify(vinculos).deleteByServicoId(uid(2));
        verify(servicos).deleteById(uid(2));
    }

    @Test
    @DisplayName("excluir 404 quando não existe")
    void excluir_naoEncontrado() {
        when(servicos.existsById(uid(2))).thenReturn(false);
        assertThatThrownBy(() -> service.excluir(uid(2))).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("listar vazio quando não há serviços")
    void listar_vazio() {
        when(servicos.findAllByOrderByNomeAsc()).thenReturn(List.of());
        assertThat(service.listar()).isEmpty();
    }

    @Test
    @DisplayName("listar marca tipo removido quando o nome não resolve")
    void listar_tipoRemovido() {
        when(servicos.findAllByOrderByNomeAsc()).thenReturn(List.of(servico(uid(1), "S")));
        when(tiposServico.findAll()).thenReturn(List.of());
        ServicoTipo v = new ServicoTipo(); v.setServicoId(uid(1)); v.setTipoServicoId(uid(50));
        when(vinculos.findByServicoIdIn(anyCollection())).thenReturn(List.of(v));
        assertThat(service.listar().get(0).tipos()).extracting(ServicoService.TipoRef::nome)
                .containsExactly("(removido)");
    }

    @Test
    @DisplayName("listar devolve o código Pleno configurado por tipo")
    void listar_comCodigoPleno() {
        when(servicos.findAllByOrderByNomeAsc()).thenReturn(List.of(servico(uid(1), "Elegance")));
        when(tiposServico.findAll()).thenReturn(List.of(tipo(uid(1), "Vistoria de entrada")));
        ServicoTipo v = new ServicoTipo();
        v.setServicoId(uid(1));
        v.setTipoServicoId(uid(1));
        v.setPlenoTipVisCodigo(42);
        when(vinculos.findByServicoIdIn(anyCollection())).thenReturn(List.of(v));

        List<ServicoService.TipoRef> tipos = service.listar().get(0).tipos();

        assertThat(tipos).hasSize(1);
        assertThat(tipos.get(0).plenoTipVisCodigo()).isEqualTo(42);
    }

    @Test
    @DisplayName("atualizarCodigoPleno grava o código na combinação servico+tipo")
    void atualizarCodigoPleno_ok() {
        ServicoTipo v = new ServicoTipo();
        v.setServicoId(uid(7));
        v.setTipoServicoId(uid(1));
        when(vinculos.findFirstByServicoIdAndTipoServicoId(uid(7), uid(1))).thenReturn(Optional.of(v));

        service.atualizarCodigoPleno(uid(7), uid(1), 42);

        assertThat(v.getPlenoTipVisCodigo()).isEqualTo(42);
        verify(vinculos).save(v);
    }

    @Test
    @DisplayName("atualizarCodigoPleno 404 quando a combinação servico+tipo não existe")
    void atualizarCodigoPleno_naoEncontrado() {
        when(vinculos.findFirstByServicoIdAndTipoServicoId(uid(7), uid(99))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizarCodigoPleno(uid(7), uid(99), 42))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("tiposDeServicos devolve os tipos distintos dos serviços")
    void tiposDeServicos_ok() {
        ServicoTipo a = new ServicoTipo(); a.setTipoServicoId(uid(1));
        ServicoTipo b = new ServicoTipo(); b.setTipoServicoId(uid(2));
        ServicoTipo c = new ServicoTipo(); c.setTipoServicoId(uid(1));
        when(vinculos.findByServicoIdIn(anyCollection())).thenReturn(List.of(a, b, c));
        assertThat(service.tiposDeServicos(List.of(uid(10), uid(11)))).containsExactly(uid(1), uid(2));
    }

    @Test
    @DisplayName("tiposDeServicos vazio para entrada vazia")
    void tiposDeServicos_vazio() {
        assertThat(service.tiposDeServicos(Set.of())).isEmpty();
        assertThat(service.tiposDeServicos(null)).isEmpty();
    }
}
