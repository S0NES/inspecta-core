package br.com.vistoria.core.modules.cadastro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CadastroRepositories {
    interface CidadeRepository extends JpaRepository<Cidade, UUID> {}
    interface ImovelRepository extends JpaRepository<Imovel, UUID> {}
    interface ClienteRepository extends JpaRepository<Cliente, UUID> {}
    /** Lookup de papéis — editável só pelo banco; sem CRUD HTTP. */
    interface IntervenientePapelRepository extends JpaRepository<IntervenientePapel, String> {
        List<IntervenientePapel> findByAtivoTrueOrderBySlug();
        boolean existsBySlugAndAtivoTrue(String slug);
    }
    interface TipoServicoRepository extends JpaRepository<TipoServico, UUID> {}

    // V3 — Serviço guarda-chuva + catálogo de faixas de referência
    interface ServicoRepository extends JpaRepository<Servico, UUID> {
        Optional<Servico> findByNomeIgnoreCase(String nome);
        List<Servico> findAllByOrderByNomeAsc();
    }
    interface ServicoTipoRepository extends JpaRepository<ServicoTipo, UUID> {
        List<ServicoTipo> findByServicoId(UUID servicoId);
        List<ServicoTipo> findByServicoIdIn(Collection<UUID> servicoIds);
        List<ServicoTipo> findByTipoServicoId(UUID tipoServicoId);
        void deleteByServicoId(UUID servicoId);
        boolean existsByServicoIdAndTipoServicoId(UUID servicoId, UUID tipoServicoId);
        Optional<ServicoTipo> findFirstByServicoIdAndTipoServicoId(UUID servicoId, UUID tipoServicoId);
    }
    interface FaixaReferenciaRepository extends JpaRepository<FaixaReferencia, Short> {
        List<FaixaReferencia> findAllByOrderByOrdemAsc();
    }
}
