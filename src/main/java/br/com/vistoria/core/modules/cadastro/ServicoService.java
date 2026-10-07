package br.com.vistoria.core.modules.cadastro;

import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ServicoRepository;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ServicoTipoRepository;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.TipoServicoRepository;
import br.com.vistoria.core.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V3 — CRUD do "Serviço" guarda-chuva (catálogo global). Só admin/suporte devem chamar
 * (RBAC no inspecta-api). Cada Serviço agrupa N Tipos de Serviço via {@code servico_tipos}.
 */
@Service
@RequiredArgsConstructor
public class ServicoService {

    private final ServicoRepository servicos;
    private final ServicoTipoRepository vinculos;
    private final TipoServicoRepository tiposServico;

    /** Serviço + os tipos que ele agrupa (id + nome), pronto pra tela de Tipagem. */
    public record ServicoView(UUID id, String nome, boolean ativo, List<TipoRef> tipos) {}
    public record TipoRef(UUID id, String nome, Integer plenoTipVisCodigo) {}

    @Transactional(readOnly = true)
    public List<ServicoView> listar() {
        List<Servico> todos = servicos.findAllByOrderByNomeAsc();
        if (todos.isEmpty()) return List.of();
        Map<UUID, String> nomeTipo = tiposServico.findAll().stream()
                .collect(Collectors.toMap(TipoServico::getId, TipoServico::getNome));
        Map<UUID, List<ServicoTipo>> porServico = vinculos
                .findByServicoIdIn(todos.stream().map(Servico::getId).toList())
                .stream().collect(Collectors.groupingBy(ServicoTipo::getServicoId));
        return todos.stream().map(s -> new ServicoView(
                s.getId(), s.getNome(), s.isAtivo(),
                porServico.getOrDefault(s.getId(), List.of()).stream()
                        .map(v -> new TipoRef(v.getTipoServicoId(),
                                nomeTipo.getOrDefault(v.getTipoServicoId(), "(removido)"),
                                v.getPlenoTipVisCodigo()))
                        .toList()
        )).toList();
    }

    @Transactional
    public ServicoView criar(String nome, Collection<UUID> tipoServicoIds) {
        String limpo = exigirNome(nome);
        servicos.findByNomeIgnoreCase(limpo).ifPresent(s -> {
            throw new IllegalStateException("Ja existe um servico chamado '" + s.getNome() + "'");
        });
        Servico s = new Servico();
        s.setNome(limpo);
        s = servicos.save(s);
        substituirTipos(s.getId(), tipoServicoIds);
        return recarregar(s.getId());
    }

    @Transactional
    public ServicoView atualizar(UUID id, String nome, Boolean ativo, Collection<UUID> tipoServicoIds) {
        Servico s = servicos.findById(id)
                .orElseThrow(() -> new NotFoundException("Servico " + id + " nao encontrado"));
        if (nome != null) {
            String limpo = exigirNome(nome);
            servicos.findByNomeIgnoreCase(limpo)
                    .filter(outro -> !outro.getId().equals(id))
                    .ifPresent(outro -> { throw new IllegalStateException("Nome ja usado por outro servico"); });
            s.setNome(limpo);
        }
        if (ativo != null) s.setAtivo(ativo);
        servicos.save(s);
        if (tipoServicoIds != null) substituirTipos(id, tipoServicoIds);
        return recarregar(id);
    }

    @Transactional
    public void excluir(UUID id) {
        if (!servicos.existsById(id)) throw new NotFoundException("Servico " + id + " nao encontrado");
        vinculos.deleteByServicoId(id);
        servicos.deleteById(id);
    }

    /**
     * Configura o código Pleno (tip_vis_codigo) para uma combinação Serviço × Tipo de
     * Serviço. {@code codigo} nulo limpa a configuração (volta ao de-para/default legado).
     */
    @Transactional
    public void atualizarCodigoPleno(UUID servicoId, UUID tipoServicoId, Integer codigo) {
        ServicoTipo v = vinculos.findFirstByServicoIdAndTipoServicoId(servicoId, tipoServicoId)
                .orElseThrow(() -> new NotFoundException(
                        "Vinculo entre servico " + servicoId + " e tipo de servico " + tipoServicoId + " nao encontrado"));
        v.setPlenoTipVisCodigo(codigo);
        vinculos.save(v);
    }

    /** Tipos de Serviço distintos cobertos por um conjunto de Serviços guarda-chuva. */
    @Transactional(readOnly = true)
    public Set<UUID> tiposDeServicos(Collection<UUID> servicoIds) {
        if (servicoIds == null || servicoIds.isEmpty()) return Set.of();
        return vinculos.findByServicoIdIn(servicoIds).stream()
                .map(ServicoTipo::getTipoServicoId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }


    /**
     * Reconcilia os vínculos Serviço↔Tipo por DIFERENÇA (não delete-all + reinsert).
     *
     * <p>O caminho antigo apagava tudo e reinseria: o Hibernate ordena os INSERT antes dos
     * DELETE no flush, então reeditar um Serviço mantendo um tipo já vinculado violava
     * {@code UNIQUE (servico_id, tipo_servico_id)} → 409. Além disso zerava o
     * {@code pleno_tip_vis_codigo} a cada edição. Agora só removemos os que saíram e
     * inserimos os que entraram; os que permanecem ficam intactos.</p>
     */
    private void substituirTipos(UUID servicoId, Collection<UUID> tipoServicoIds) {
        if (tipoServicoIds == null) return;
        Set<UUID> desejados = new LinkedHashSet<>(tipoServicoIds);
        Map<UUID, TipoServico> existentes = tiposServico.findAllById(desejados).stream()
                .collect(Collectors.toMap(TipoServico::getId, Function.identity()));
        for (UUID tid : desejados) {
            if (!existentes.containsKey(tid))
                throw new NotFoundException("Tipo de servico " + tid + " nao encontrado");
        }

        List<ServicoTipo> atuais = vinculos.findByServicoId(servicoId);
        Set<UUID> jaVinculados = atuais.stream().map(ServicoTipo::getTipoServicoId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ServicoTipo> aRemover = atuais.stream()
                .filter(v -> !desejados.contains(v.getTipoServicoId()))
                .toList();
        if (!aRemover.isEmpty()) vinculos.deleteAll(aRemover);

        for (UUID tid : desejados) {
            if (jaVinculados.contains(tid)) continue;
            ServicoTipo v = new ServicoTipo();
            v.setServicoId(servicoId);
            v.setTipoServicoId(tid);
            vinculos.save(v);
        }
    }

    private ServicoView recarregar(UUID id) {
        return listar().stream().filter(v -> v.id().equals(id)).findFirst()
                .orElseThrow(() -> new NotFoundException("Servico " + id + " nao encontrado"));
    }

    private static String exigirNome(String nome) {
        String limpo = nome == null ? "" : nome.trim();
        if (limpo.isEmpty()) throw new IllegalArgumentException("Nome do servico obrigatorio");
        return limpo;
    }
}
