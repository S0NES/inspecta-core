package br.com.vistoria.core.modules.financeiro;

import br.com.vistoria.core.modules.financeiro.FinanceiroRepositories.PagamentoVistoriadorRepository;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoService;
import br.com.vistoria.core.modules.solicitacao.SolicitacaoServico;
import br.com.vistoria.core.shared.util.Periodo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PagamentoVistoriadorService {

    private final PagamentoVistoriadorRepository pagamentos;
    private final SolicitacaoService solicitacaoService;

    public record Extrato(String periodo, List<SolicitacaoServico> servicos, BigDecimal total) {}

    @Transactional(readOnly = true)
    public Extrato extrato(UUID vistoriadorId, String periodo) {
        var range = Periodo.range(periodo);
        List<SolicitacaoServico> servicos = solicitacaoService.concluidosNoPeriodo(vistoriadorId, range.inicio(), range.fim());
        BigDecimal total = servicos.stream()
                .map(s -> s.getRepasseSnapshot() != null ? s.getRepasseSnapshot() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Extrato(periodo, servicos, total);
    }

    @Transactional
    public PagamentoVistoriador registrar(UUID vistoriadorId, String periodo, BigDecimal valor, UUID comprovanteArquivoId) {
        PagamentoVistoriador p = new PagamentoVistoriador();
        p.setVistoriadorId(vistoriadorId);
        p.setPeriodo(periodo);
        p.setValor(valor);
        p.setComprovanteArquivoId(comprovanteArquivoId);
        return pagamentos.save(p);
    }
}
