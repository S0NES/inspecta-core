package br.com.vistoria.core.modules.solicitacao;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ClienteRepository;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.IntervenientePapelRepository;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ImovelRepository;
import br.com.vistoria.core.modules.cadastro.Cliente;
import br.com.vistoria.core.modules.cadastro.Imovel;
import br.com.vistoria.core.modules.contrato.Contrato;
import br.com.vistoria.core.modules.contrato.ContratoNaoConfiguradoException;
import br.com.vistoria.core.modules.contrato.PrecoService;
import br.com.vistoria.core.modules.financeiro.ExtratoItem;
import br.com.vistoria.core.modules.financeiro.FinanceiroRepositories.ExtratoItemRepository;
import br.com.vistoria.core.modules.integracao.IntegracaoSyncService;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoRepositories.*;
import br.com.vistoria.core.modules.solicitacao.dto.CreateSolicitacaoRequest;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import br.com.vistoria.core.shared.validation.PessoaDocumento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SolicitacaoService {

    private final SolicitacaoRepository solicitacoes;
    private final ServicoRepository servicos;
    private final SolicitacaoIntervenienteRepository intervenientes;
    private final ImovelRepository imoveis;
    private final ClienteRepository clientes;
    private final IntervenientePapelRepository papeis;
    private final PrecoService precoService;
    private final OutboxService outbox;
    private final IntegracaoSyncService integracaoSync;
    private final ExtratoItemRepository extratoItens;

    /**
     * Cria uma solicitação (fluxo B2B — D1/D2/D3, plano-b2b-billing.md):
     * <ul>
     *   <li>{@code imobiliaria_id} = {@code imovel.imobiliaria_id}. Pode ser {@code null}
     *       (porta reservada para um futuro B2C sem imobiliária) — nesse caso NÃO lança.</li>
     *   <li>{@code cliente_id} opcional: se vier, cliente e imóvel têm de ser da mesma
     *       imobiliária; se não vier, não exige {@code Cliente}.</li>
     *   <li>{@code valorAluguel}/{@code mobiliado} são da solicitação (não do imóvel);
     *       obrigatórios só quando há imobiliária E o contrato ativo é modo percentual.</li>
     *   <li>Preço via {@link PrecoService} com os campos da request (sem contrato → 422).</li>
     *   <li>≥1 interveniente da solicitação (nome/cpf/e-mail válidos).</li>
     *   <li>Se há imobiliária: para CADA serviço publica {@code jobs.assignment.requested}
     *       na criação (mesmo shape de {@link #aplicarPagamentoConfirmado}) e insere um
     *       {@code extrato_itens} em aberto ({@code fatura_id = null}). NÃO publica
     *       {@code PAYMENT_CHARGE_REQUESTED} — a cobrança é consolidada depois.</li>
     * </ul>
     */
    @Transactional
    public Solicitacao criar(CreateSolicitacaoRequest req, UUID criadoPor) {
        Imovel imovel = imoveis.findById(req.imovelId())
                .orElseThrow(() -> new NotFoundException("Imovel " + req.imovelId()));
        UUID imobiliariaId = imovel.getImobiliariaId();   // pode ser null (porta B2C futura)

        UUID clienteId = null;
        if (req.clienteId() != null) {
            Cliente cliente = clientes.findById(req.clienteId())
                    .orElseThrow(() -> new NotFoundException("Cliente " + req.clienteId()));
            if (imobiliariaId == null || cliente.getImobiliariaId() == null
                    || !cliente.getImobiliariaId().equals(imobiliariaId))
                throw new IllegalArgumentException("cliente_imobiliaria_divergente");
            clienteId = cliente.getId();
        }

        // aluguel/mobiliado: obrigatórios só com imobiliária + contrato ativo modo percentual.
        if (imobiliariaId != null) {
            boolean percentual = precoService.contratoAtivo(imobiliariaId)
                    .map(c -> c.getModo() == Contrato.Modo.percentual)
                    .orElse(false);
            if (percentual && (req.valorAluguel() == null || req.mobiliado() == null))
                throw new IllegalArgumentException("aluguel_e_mobiliado_obrigatorios_no_modo_percentual");
        }

        // Valida os intervenientes ANTES de persistir qualquer coisa (o @NotEmpty do DTO já
        // barra lista vazia; aqui vêm os códigos estáveis por campo).
        List<SolicitacaoInterveniente> pessoas = new ArrayList<>();
        for (var it : req.intervenientes()) {
            if (it.papelSlug() == null || !papeis.existsBySlugAndAtivoTrue(it.papelSlug()))
                throw new IllegalArgumentException("papel_invalido");
            SolicitacaoInterveniente p = new SolicitacaoInterveniente();
            p.setPapelSlug(it.papelSlug());
            p.setNome(PessoaDocumento.requireNomeCompleto(it.nome()));
            p.setCpf(PessoaDocumento.requireCpf(it.cpf()));
            p.setEmail(PessoaDocumento.requireEmail(it.email()));
            if (it.telefone() != null && !it.telefone().isBlank())
                p.setTelefone(PessoaDocumento.requireTelefone(it.telefone()));
            p.setAcompanhante(Boolean.TRUE.equals(it.acompanhante()));
            pessoas.add(p);
        }

        Solicitacao s = new Solicitacao();
        s.setImobiliariaId(imobiliariaId);
        s.setClienteId(clienteId);
        s.setImovelId(imovel.getId());
        s.setUrgente(Boolean.TRUE.equals(req.urgente()));
        s.setChavesRetirada(req.chavesRetirada());
        s.setChavesDevolucao(req.chavesDevolucao());
        s.setValorAluguel(req.valorAluguel());
        s.setMobiliado(req.mobiliado());
        s.setOrigem(req.origem());
        s.setCriadoPor(criadoPor);
        s = solicitacoes.saveAndFlush(s);

        for (SolicitacaoInterveniente p : pessoas) {
            p.setSolicitacaoId(s.getId());
            intervenientes.save(p);
        }

        List<SolicitacaoServico> criados = new ArrayList<>();
        List<PrecoService.Preco> precos = new ArrayList<>();
        for (var item : req.servicos()) {
            SolicitacaoServico ss = new SolicitacaoServico();
            ss.setSolicitacaoId(s.getId());
            ss.setTipoServicoId(item.tipoServicoId());
            ss.setDataDesejada(item.dataDesejada());
            ss.setProductId(UUID.randomUUID());

            PrecoService.Preco preco;
            try {
                // V4/D2: mobiliado/valorAluguel vêm da solicitação (não do imóvel); a metragem
                // continua estrutural (do imóvel).
                preco = precoService.resolver(imobiliariaId, item.tipoServicoId(),
                        imovel.getMetragem(), req.mobiliado(), req.valorAluguel());
            } catch (NotFoundException e) {
                throw new ContratoNaoConfiguradoException(e.getMessage());
            }
            ss.setPrecoSnapshot(preco.valor());
            ss.setRepasseSnapshot(preco.repasse());
            servicos.save(ss);
            criados.add(ss);
            precos.add(preco);
        }
        servicos.flush();

        // D3: solicitação com imobiliária é sempre pós-paga — a atribuição sai JÁ na criação
        // e o valor entra num item de extrato em aberto, liquidado depois por cobrança
        // consolidada. NADA de PAYMENT_CHARGE_REQUESTED aqui. O caso sem imobiliária
        // (B2C futuro) segue sem atribuição/checkout — bloqueado até a spec existir.
        if (imobiliariaId != null) {
            for (int i = 0; i < criados.size(); i++) {
                SolicitacaoServico ss = criados.get(i);
                outbox.publish(EventTypes.ASSIGNMENT_REQUESTED, Map.of(
                        "eventId", "assign-pay-" + ss.getId(),
                        "servicoId", ss.getId()));
                ExtratoItem ei = new ExtratoItem();
                ei.setImobiliariaId(imobiliariaId);
                ei.setSolicitacaoServicoId(ss.getId());
                ei.setValor(precos.get(i).valor());
                ei.setFaturaId(null);
                extratoItens.save(ei);
            }
        }

        // cadastro externo (sol_codigo informado) -> vincula; senão -> espelhamento assíncrono.
        if (req.plenoIdExterno() != null && !req.plenoIdExterno().isBlank()) {
            integracaoSync.vincularExterno("solicitacao", s.getId(), req.plenoIdExterno().trim());
        } else {
            integracaoSync.enfileirar("solicitacao", s.getId());
        }
        return s;
    }

    @Transactional(readOnly = true)
    public Solicitacao get(UUID id) {
        return solicitacoes.findById(id).orElseThrow(() -> new NotFoundException("Solicitacao " + id));
    }

    @Transactional(readOnly = true)
    public BigDecimal totalServicos(UUID solicitacaoId) {
        return servicos.findBySolicitacaoId(solicitacaoId).stream()
                .map(SolicitacaoServico::getPrecoSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Intervenientes da solicitação dona de um serviço — fonte dos signatários da assinatura (Autentique). */
    @Transactional(readOnly = true)
    public List<SolicitacaoInterveniente> intervenientesDoServico(UUID servicoId) {
        SolicitacaoServico ss = servicos.findById(servicoId)
                .orElseThrow(() -> new NotFoundException("Servico " + servicoId));
        return intervenientes.findBySolicitacaoId(ss.getSolicitacaoId());
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoServico> concluidosNoPeriodo(UUID vistoriadorId, java.time.Instant inicio, java.time.Instant fim) {
        return servicos.concluidosNoPeriodo(vistoriadorId, inicio, fim);
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoServico> faturamentoNoPeriodo(UUID imobiliariaId, java.time.Instant inicio, java.time.Instant fim) {
        return servicos.faturamentoNoPeriodo(imobiliariaId, inicio, fim);
    }

    @Transactional(readOnly = true)
    public String statusDerivado(UUID solicitacaoId) {
        List<SolicitacaoServico> list = servicos.findBySolicitacaoId(solicitacaoId);
        if (list.isEmpty()) return "vazia";
        boolean todasConcluidas = list.stream().allMatch(s -> s.getStatus() == SolicitacaoServico.Status.concluida);
        if (todasConcluidas) return "concluida";
        boolean todasCanceladas = list.stream().allMatch(s -> s.getStatus() == SolicitacaoServico.Status.cancelada);
        if (todasCanceladas) return "cancelada";
        boolean algumaAtiva = list.stream().anyMatch(s ->
                s.getStatus() == SolicitacaoServico.Status.em_execucao ||
                s.getStatus() == SolicitacaoServico.Status.agendada ||
                s.getStatus() == SolicitacaoServico.Status.atribuida);
        return algumaAtiva ? "em_andamento" : "pendente";
    }

    /**
     * Imobiliária dona de um serviço, via solicitação. Usado pelo inspecta-worker para escolher o
     * template de PDF do laudo. Nunca {@code null} em linha nova (a solicitação sempre herda a
     * imobiliária do imóvel).
     */
    @Transactional(readOnly = true)
    public UUID imobiliariaDoServico(UUID servicoId) {
        SolicitacaoServico ss = servicos.findById(servicoId)
                .orElseThrow(() -> new NotFoundException("Servico " + servicoId));
        return get(ss.getSolicitacaoId()).getImobiliariaId();
    }

    /**
     * Transição "pagamento confirmado": marca {@code payment_status = 'pago'} e publica um
     * {@code jobs.assignment.requested} para cada serviço ainda {@code pendente} da solicitação.
     * Chamado por {@link br.com.vistoria.core.modules.financeiro.PagamentoService#aplicarEvento}
     * apenas na aresta {@code !eraPago && status == pago}, então é naturalmente idempotente:
     * um replay do webhook pago não chega aqui de novo.
     *
     * <p>B2C (Página 4, {@code imobiliariaId == null}): o vistoriador já foi escolhido e
     * reservado no rascunho (hold síncrono — ver {@code DisponibilidadeService.escolher} +
     * o bind em {@code SolicitacaoB2cService.criarRascunhoB2c}, inspecta-api), então o serviço já está
     * em {@code atribuida}, não {@code pendente} — nunca entra no ramo de publicar
     * {@code ASSIGNMENT_REQUESTED} acima. Aqui só promove pra {@code agendada} e dispara o
     * espelhamento Pleno (que o B2B já faz na criação, mas o B2C só pode fazer aqui — antes do
     * pagamento a Pleno não deveria saber da solicitação).</p>
     *
     * @return quantos eventos de atribuição foram publicados
     */
    @Transactional
    public int aplicarPagamentoConfirmado(UUID solicitacaoId) {
        Solicitacao s = solicitacoes.findById(solicitacaoId)
                .orElseThrow(() -> new NotFoundException("Solicitacao " + solicitacaoId));
        s.setPaymentStatus("pago");
        boolean b2c = s.getImobiliariaId() == null;
        int publicados = 0;
        boolean algumB2cPromovido = false;
        for (SolicitacaoServico ss : servicos.findBySolicitacaoId(solicitacaoId)) {
            if (ss.getStatus() == SolicitacaoServico.Status.pendente) {
                outbox.publish(EventTypes.ASSIGNMENT_REQUESTED, Map.of(
                        "eventId", "assign-pay-" + ss.getId(),
                        "servicoId", ss.getId()));
                publicados++;
            } else if (b2c && ss.getStatus() == SolicitacaoServico.Status.atribuida) {
                ss.setStatus(SolicitacaoServico.Status.agendada);
                algumB2cPromovido = true;
            }
        }
        if (b2c && algumB2cPromovido) {
            integracaoSync.enfileirar("solicitacao", s.getId());
        }
        return publicados;
    }

    /** Marca o {@code payment_status} da solicitação (ex.: {@code checkout_ready}, {@code erro}). */
    @Transactional
    public void marcarPaymentStatus(UUID solicitacaoId, String paymentStatus) {
        Solicitacao s = solicitacoes.findById(solicitacaoId)
                .orElseThrow(() -> new NotFoundException("Solicitacao " + solicitacaoId));
        s.setPaymentStatus(paymentStatus);
    }

    @Transactional
    public void cancelarServico(UUID servicoId) {
        SolicitacaoServico ss = servicos.findById(servicoId)
                .orElseThrow(() -> new NotFoundException("Servico " + servicoId));
        if (ss.getStatus() == SolicitacaoServico.Status.em_execucao)
            throw new IllegalStateException("Servico em execucao nao pode ser cancelado");
        ss.setStatus(SolicitacaoServico.Status.cancelada);
    }
}
