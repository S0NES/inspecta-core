package br.com.vistoria.core.modules.b2cservico;

import br.com.vistoria.core.modules.cadastro.CadastroRepositories.TipoServicoRepository;
import br.com.vistoria.core.modules.cadastro.TipoServico;
import br.com.vistoria.core.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD do catálogo {@link B2cService} — admin/suporte. Criar aqui também cria o
 * {@code tipos_servico} correspondente (necessário porque
 * {@code solicitacao_servicos.tipo_servico_id} continua NOT NULL), pra quem
 * cadastra não precisar conhecer o catálogo B2B.
 */
@Service
@RequiredArgsConstructor
public class B2cServicoService {

    private final B2cServiceRepository repository;
    private final TipoServicoRepository tiposServico;

    public record CreateRequest(String nome, String descricao, String icone, Short ordem) {}
    public record PatchRequest(String nome, String descricao, String icone, Boolean ativo, Short ordem) {}

    @Transactional(readOnly = true)
    public List<B2cService> listar(boolean apenasAtivos) {
        return apenasAtivos ? repository.findAllByAtivoTrueOrderByOrdemAscNomeAsc()
                             : repository.findAllByOrderByOrdemAscNomeAsc();
    }

    @Transactional
    public B2cService criar(CreateRequest req) {
        if (req.nome() == null || req.nome().isBlank())
            throw new IllegalArgumentException("Nome obrigatorio");

        TipoServico ts = new TipoServico();
        ts.setNome(req.nome().trim());
        ts.setAtivo(true);
        ts = tiposServico.save(ts);

        B2cService s = new B2cService();
        s.setNome(req.nome().trim());
        s.setDescricao(req.descricao() != null ? req.descricao() : "");
        s.setIcone(req.icone() != null ? req.icone() : "");
        s.setTipoServicoId(ts.getId());
        s.setOrdem(req.ordem() != null ? req.ordem() : 0);
        return repository.save(s);
    }

    @Transactional
    public B2cService atualizar(UUID id, PatchRequest req) {
        B2cService s = repository.findById(id).orElseThrow(() -> new NotFoundException("Servico B2C " + id));
        if (req.nome() != null && !req.nome().isBlank()) s.setNome(req.nome().trim());
        if (req.descricao() != null) s.setDescricao(req.descricao());
        if (req.icone() != null) s.setIcone(req.icone());
        if (req.ativo() != null) s.setAtivo(req.ativo());
        if (req.ordem() != null) s.setOrdem(req.ordem());
        return repository.save(s);
    }
}
