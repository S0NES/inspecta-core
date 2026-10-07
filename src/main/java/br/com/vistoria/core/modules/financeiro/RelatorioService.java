package br.com.vistoria.core.modules.financeiro;

import br.com.vistoria.core.modules.contrato.Contrato;
import br.com.vistoria.core.modules.contrato.PrecoService;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoService;
import br.com.vistoria.core.shared.util.Periodo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RelatorioService {

    private final SolicitacaoService solicitacaoService;
    private final PrecoService precoService;
    private final br.com.vistoria.core.modules.financeiro.FinanceiroRepositories.PagamentoRepository pagamentos;
    private final br.com.vistoria.core.modules.imobiliaria.ImobiliariaRepository imobiliarias;
    private final br.com.vistoria.core.modules.solicitacao.SolicitacaoRepositories.ServicoRepository servicos;
    private final br.com.vistoria.core.modules.laudo.LaudoRepository laudos;

    public record RelatorioImobiliaria(UUID imobiliariaId, String mes, int volume,
                                       BigDecimal faturamento, BigDecimal faturamentoMinimo,
                                       boolean minimoAplicado, BigDecimal faturamentoCobrado) {}

    /**
     * Linha do relatório cross-imobiliária no mês (pendencias.md § 1 / § 5):
     * <ul>
     *   <li>{@code servicosPagos}/{@code valorTotal} — cobranças pagas;</li>
     *   <li>{@code pagamentosPendentes}/{@code valorPendente} — cobranças em aberto
     *       (criando/pendente);</li>
     *   <li>{@code volumeMensal}/{@code volumeExcedido} — teto do contrato ativo e
     *       se os serviços pagos passaram dele.</li>
     * </ul>
     * A linha aparece se a imobiliária tem qualquer cobrança (paga ou em aberto) no mês.
     */
    public record ConsumoImobiliaria(UUID imobiliariaId, String imobiliariaNome,
                                     long servicosPagos, BigDecimal valorTotal,
                                     long pagamentosPendentes, BigDecimal valorPendente,
                                     Integer volumeMensal, boolean volumeExcedido) {}

    @Transactional(readOnly = true)
    public java.util.List<ConsumoImobiliaria> consumoCrossImobiliaria(String mes) {
        var range = Periodo.range(mes);
        var nomes = new java.util.HashMap<UUID, String>();
        imobiliarias.findAll().forEach(i ->
                nomes.put(i.getId(), i.getNomeFantasia() != null && !i.getNomeFantasia().isBlank()
                        ? i.getNomeFantasia() : i.getRazaoSocial()));

        var pagos = pagamentos.consumoPorImobiliaria(range.inicio(), range.fim());
        var pendentes = pagamentos.pendentePorImobiliaria(range.inicio(), range.fim());

        var pagoPorImob = pagos.stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.getImobiliariaId(), r -> r, (a, b) -> a));
        var pendPorImob = pendentes.stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.getImobiliariaId(), r -> r, (a, b) -> a));

        var ids = new java.util.LinkedHashSet<UUID>();
        pagoPorImob.keySet().forEach(ids::add);
        pendPorImob.keySet().forEach(ids::add);

        var contratoPorImob = precoService.contratosAtivos(ids);

        return ids.stream()
                .map(id -> {
                    var pago = pagoPorImob.get(id);
                    var pend = pendPorImob.get(id);
                    long qtdPagos = pago == null ? 0 : pago.getServicosPagos();
                    BigDecimal totalPago = pago == null || pago.getValorTotal() == null
                            ? BigDecimal.ZERO : pago.getValorTotal();
                    long qtdPend = pend == null ? 0 : pend.getPagamentosPendentes();
                    BigDecimal totalPend = pend == null || pend.getValorPendente() == null
                            ? BigDecimal.ZERO : pend.getValorPendente();
                    Contrato contrato = contratoPorImob.get(id);
                    Integer volumeMensal = contrato == null ? null : contrato.getVolumeMensal();
                    boolean excedido = volumeMensal != null && volumeMensal > 0 && qtdPagos > volumeMensal;
                    return new ConsumoImobiliaria(id,
                            nomes.getOrDefault(id, "Imobiliária #" + id),
                            qtdPagos, totalPago, qtdPend, totalPend, volumeMensal, excedido);
                })
                .sorted((a, b) -> b.valorTotal().compareTo(a.valorTotal()))
                .toList();
    }

    @Transactional(readOnly = true)
    public RelatorioImobiliaria imobiliaria(UUID imobiliariaId, String mes) {
        var range = Periodo.range(mes);
        var servicos = solicitacaoService.faturamentoNoPeriodo(imobiliariaId, range.inicio(), range.fim());
        BigDecimal faturamento = servicos.stream()
                .map(br.com.vistoria.core.modules.solicitacao.SolicitacaoServico::getPrecoSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal faturamentoMinimo = precoService.contratoAtivo(imobiliariaId)
                .map(Contrato::getFaturamentoMinimo)
                .orElse(null);

        boolean minimoAplicado = faturamentoMinimo != null && faturamento.compareTo(faturamentoMinimo) < 0;
        BigDecimal faturamentoCobrado = minimoAplicado ? faturamentoMinimo : faturamento;

        return new RelatorioImobiliaria(imobiliariaId, mes, servicos.size(),
                faturamento, faturamentoMinimo, minimoAplicado, faturamentoCobrado);
    }

    /**
     * Extrato por vistoria: uma linha por {@code solicitacao_servico} no mês de criação.
     * {@code imobiliariaId} nulo = todas (admin/suporte). RLS ainda recorta o tenant.
     * {@code nome} casa endereço do imóvel, nome da imobiliária ou id da solicitação.
     */
    @Transactional(readOnly = true)
    public VistoriasPage vistorias(String mes, UUID imobiliariaId, String nome,
                                   org.springframework.data.domain.Pageable pageable) {
        var range = Periodo.range(mes);
        boolean temImob = imobiliariaId != null;
        String termo = nome == null ? "" : nome.trim().toLowerCase();
        boolean temNome = !termo.isBlank();
        String like = temNome ? "%" + termo + "%" : "";
        UUID imobBind = temImob ? imobiliariaId : new UUID(0L, 0L);

        var page = servicos.listarVistorias(range.inicio(), range.fim(),
                temImob, imobBind, temNome, like, pageable);
        java.math.BigDecimal total = servicos.somarVistorias(range.inicio(), range.fim(),
                temImob, imobBind, temNome, like);
        if (total == null) total = java.math.BigDecimal.ZERO;

        var ids = page.getContent().stream().map(VistoriaExtratoLinha::id).toList();
        var laudoPorServico = new java.util.HashMap<UUID, UUID>();
        if (!ids.isEmpty()) {
            laudos.findBySolicitacaoServicoIdIn(ids).forEach(l ->
                    laudoPorServico.putIfAbsent(l.getSolicitacaoServicoId(), l.getId()));
        }

        var content = page.getContent().stream()
                .map(linha -> linha.withLaudo(laudoPorServico.get(linha.id())))
                .toList();
        return new VistoriasPage(content, page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), total);
    }
}
