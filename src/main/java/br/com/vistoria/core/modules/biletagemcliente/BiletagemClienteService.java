package br.com.vistoria.core.modules.biletagemcliente;

import br.com.vistoria.core.modules.b2cservico.B2cServiceRepository;
import br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteRepositories.BiletagemClienteFaixaRepository;
import br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteRepositories.BiletagemClientePercentualRepository;
import br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteRepositories.BiletagemClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Biletagem Cliente (Página 3): preço B2C global, um contrato só, chaveado por {@code b2c_service}. */
@Service
@RequiredArgsConstructor
public class BiletagemClienteService {

    private final BiletagemClienteRepository config;
    private final BiletagemClientePercentualRepository percentuais;
    private final BiletagemClienteFaixaRepository faixas;
    private final B2cServiceRepository b2cServicos;

    public record ServicoItem(UUID id, String nome, String descricao, String icone) {}

    public record PercentualItem(UUID b2cServiceId, BigDecimal percentual, BigDecimal minimo) {}

    public record FaixaItem(UUID b2cServiceId, Short faixaReferenciaId,
                            BigDecimal valorSemMob, BigDecimal valorComMob) {}

    public record BiletagemClienteView(BiletagemCliente.Modo modo, List<ServicoItem> servicos,
                                       List<PercentualItem> percentuais, List<FaixaItem> faixas) {}

    @Transactional(readOnly = true)
    public BiletagemClienteView get() {
        BiletagemCliente.Modo modo = config.findById(Boolean.TRUE)
                .map(BiletagemCliente::getModo).orElse(BiletagemCliente.Modo.faixa);
        List<ServicoItem> servicos = b2cServicos.findAllByAtivoTrueOrderByOrdemAscNomeAsc().stream()
                .map(s -> new ServicoItem(s.getId(), s.getNome(), s.getDescricao(), s.getIcone()))
                .toList();
        List<PercentualItem> p = percentuais.findAll().stream()
                .map(x -> new PercentualItem(x.getB2cServiceId(), x.getPercentual(), x.getMinimo())).toList();
        List<FaixaItem> f = faixas.findAll().stream()
                .map(x -> new FaixaItem(x.getB2cServiceId(), x.getFaixaReferenciaId(), x.getValorSemMob(), x.getValorComMob()))
                .toList();
        return new BiletagemClienteView(modo, servicos, p, f);
    }

    /** Substitui a config inteira (modo + linhas do modo escolhido) — igual ao "showSaveButton" do B2B. */
    @Transactional
    public BiletagemClienteView salvar(BiletagemCliente.Modo modo, List<PercentualItem> novosPercentuais,
                                       List<FaixaItem> novasFaixas) {
        for (UUID id : idsDoModo(modo, novosPercentuais, novasFaixas)) {
            if (!b2cServicos.existsById(id)) throw new IllegalArgumentException("Servico B2C invalido: " + id);
        }

        BiletagemCliente c = config.findById(Boolean.TRUE).orElseGet(BiletagemCliente::new);
        c.setModo(modo);
        config.save(c);

        if (modo == BiletagemCliente.Modo.percentual) {
            if (novosPercentuais == null || novosPercentuais.isEmpty())
                throw new IllegalArgumentException("Informe ao menos um percentual");
            percentuais.deleteAll();
            for (PercentualItem item : novosPercentuais) {
                BiletagemClientePercentual e = new BiletagemClientePercentual();
                e.setB2cServiceId(item.b2cServiceId());
                e.setPercentual(item.percentual());
                e.setMinimo(item.minimo());
                percentuais.save(e);
            }
        } else {
            if (novasFaixas == null || novasFaixas.isEmpty())
                throw new IllegalArgumentException("Informe ao menos uma faixa");
            faixas.deleteAll();
            for (FaixaItem item : novasFaixas) {
                BiletagemClienteFaixa e = new BiletagemClienteFaixa();
                e.setB2cServiceId(item.b2cServiceId());
                e.setFaixaReferenciaId(item.faixaReferenciaId());
                e.setValorSemMob(item.valorSemMob());
                e.setValorComMob(item.valorComMob());
                faixas.save(e);
            }
        }
        return get();
    }

    private List<UUID> idsDoModo(BiletagemCliente.Modo modo, List<PercentualItem> p, List<FaixaItem> f) {
        if (modo == BiletagemCliente.Modo.percentual)
            return p == null ? List.of() : p.stream().map(PercentualItem::b2cServiceId).toList();
        return f == null ? List.of() : f.stream().map(FaixaItem::b2cServiceId).toList();
    }
}
