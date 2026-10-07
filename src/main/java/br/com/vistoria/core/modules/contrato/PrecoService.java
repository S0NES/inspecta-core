package br.com.vistoria.core.modules.contrato;

import br.com.vistoria.core.modules.biletagemcliente.BiletagemCliente;
import br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteFaixa;
import br.com.vistoria.core.modules.biletagemcliente.BiletagemClientePercentual;
import br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteRepositories.BiletagemClienteFaixaRepository;
import br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteRepositories.BiletagemClientePercentualRepository;
import br.com.vistoria.core.modules.biletagemcliente.BiletagemClienteRepositories.BiletagemClienteRepository;
import br.com.vistoria.core.modules.cadastro.FaixaReferencia;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.FaixaReferenciaRepository;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ServicoTipoRepository;
import br.com.vistoria.core.modules.contrato.ContratoRepositories.*;
import br.com.vistoria.core.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrecoService {

    private final ContratoRepository contratos;
    private final FaixaRepository faixas;
    private final RepasseRepository repasses;
    private final ServicoRepository servicos;
    private final ContratoServicoGrupoRepository grupos;
    private final ServicoTipoRepository servicoTipos;
    private final FaixaReferenciaRepository faixaReferencia;
    private final BiletagemClienteRepository biletagemCliente;
    private final BiletagemClientePercentualRepository biletagemClientePercentual;
    private final BiletagemClienteFaixaRepository biletagemClienteFaixa;

    public record Preco(BigDecimal valor, BigDecimal repasse, UUID faixaId) {}

    @Transactional(readOnly = true)
    public Optional<Contrato> contratoAtivo(UUID imobiliariaId) {
        return contratos.findFirstByImobiliariaIdAndStatus(imobiliariaId, "ativo");
    }

    /** Contrato ativo de um lote de imobiliárias, em uma query — evita N+1 em relatórios. */
    @Transactional(readOnly = true)
    public Map<UUID, Contrato> contratosAtivos(Collection<UUID> imobiliariaIds) {
        if (imobiliariaIds == null || imobiliariaIds.isEmpty()) return Map.of();
        return contratos.findByImobiliariaIdInAndStatus(imobiliariaIds, "ativo").stream()
                .collect(Collectors.toMap(Contrato::getImobiliariaId, Function.identity(), (a, b) -> a));
    }

    /**
     * @param mobiliado          da solicitação (V4/D2) — {@code null} tratado como {@code false}
     * @param baseParaPercentual valor do aluguel da solicitação; obrigatório no modo percentual
     */
    @Transactional(readOnly = true)
    public Preco resolver(UUID imobiliariaId, UUID tipoServicoId, BigDecimal metragem,
                          Boolean mobiliado, BigDecimal baseParaPercentual) {
        boolean mob = Boolean.TRUE.equals(mobiliado);
        Contrato c = contratos.findFirstByImobiliariaIdAndStatus(imobiliariaId, "ativo")
                .orElseThrow(() -> new NotFoundException("Contrato ativo nao encontrado para imobiliaria " + imobiliariaId));

        boolean servicoAtivo = servicos.findFirstByContratoIdAndTipoServicoId(c.getId(), tipoServicoId)
                .map(ContratoServico::isAtivo)
                .orElse(false);
        if (!servicoAtivo)
            throw new NotFoundException("Servico " + tipoServicoId + " nao ativado no contrato " + c.getId());

        if (c.getModo() == Contrato.Modo.percentual) {
            if (baseParaPercentual == null) throw new IllegalArgumentException("Base para percentual obrigatoria");

            // Valores por Serviço guarda-chuva têm precedência; o contrato é só fallback.
            BigDecimal percentual = c.getPercentual();
            BigDecimal minimo = c.getPercentualMinimo();
            var grupo = servicoTipos.findByTipoServicoId(tipoServicoId).stream()
                    .map(st -> grupos.findFirstByContratoIdAndServicoId(c.getId(), st.getServicoId()))
                    .filter(Optional::isPresent).map(Optional::get)
                    .findFirst();
            if (grupo.isPresent()) {
                if (grupo.get().getPercentual() != null) percentual = grupo.get().getPercentual();
                if (grupo.get().getPercentualMinimo() != null) minimo = grupo.get().getPercentualMinimo();
            }
            if (percentual == null) throw new NotFoundException(
                    "Percentual nao configurado para o servico " + tipoServicoId + " no contrato " + c.getId());

            BigDecimal valor = baseParaPercentual.multiply(percentual)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (minimo != null && valor.compareTo(minimo) < 0) valor = minimo.setScale(2, RoundingMode.HALF_UP);
            return new Preco(valor, null, null);
        }

        // Os valores são por (Serviço guarda-chuva × faixa de referência), NÃO por
        // tipo_servico_id. Resolve o(s) Serviço(s) guarda-chuva do tipo e usa o primeiro
        // que está no contrato (contrato_servico_grupos) E tem faixas configuradas.
        // Sem fallback legado por tipo_servico_id (o inspecta-api legado usa a FaixaRepository direto).
        UUID servicoId = servicoTipos.findByTipoServicoId(tipoServicoId).stream()
                .map(br.com.vistoria.core.modules.cadastro.ServicoTipo::getServicoId)
                .filter(sid -> grupos.findFirstByContratoIdAndServicoId(c.getId(), sid).isPresent())
                .filter(sid -> !faixas.findByContratoIdAndServicoId(c.getId(), sid).isEmpty())
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Servico guarda-chuva sem faixas configuradas no contrato " + c.getId()
                                + " para o tipo de servico " + tipoServicoId));

        // Faixa de referência que contém a metragem (faixa 5 = metragemMax NULL => sem teto).
        FaixaReferencia faixaRef = faixaReferencia.findAllByOrderByOrdemAsc().stream()
                .filter(fr -> metragem.compareTo(fr.getMetragemMin()) >= 0
                        && (fr.getMetragemMax() == null || metragem.compareTo(fr.getMetragemMax()) <= 0))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Metragem " + metragem + " fora das faixas de referencia"));

        ContratoFaixa f = faixas.findFirstByContratoIdAndServicoIdAndFaixaReferenciaId(
                        c.getId(), servicoId, faixaRef.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Servico guarda-chuva sem faixas configuradas no contrato " + c.getId()
                                + " para a faixa de referencia " + faixaRef.getId()));

        boolean faixaCinco = faixaRef.getMetragemMax() == null;
        if (!faixaCinco)
            return montar(f, mob, f.getValorSemMob(), f.getValorComMob());

        // Faixa 5: cobra o incremento Z só a cada passo COMPLETO de metragem excedente
        // acima do piso da faixa de referência — floor((metragem - pisoRef) / paStepMetragem).
        // paStepMetragem default 75 m² quando nulo. Decisão 2026-09-03: excedente parcial
        // não antecipa o próximo degrau (FLOOR).
        BigDecimal step = f.getPaStepMetragem() != null ? f.getPaStepMetragem() : BigDecimal.valueOf(75);
        BigDecimal acrescimo = BigDecimal.ZERO;
        if (f.getPaStepValor() != null && step.signum() > 0) {
            BigDecimal excedente = metragem.subtract(faixaRef.getMetragemMin());
            BigDecimal passos = excedente.divide(step, 0, RoundingMode.FLOOR);
            acrescimo = passos.multiply(f.getPaStepValor());
        }
        return montar(f, mob,
                f.getValorSemMob().add(acrescimo),
                f.getValorComMob().add(acrescimo));
    }

    private Preco montar(ContratoFaixa f, boolean mobiliado, BigDecimal semMob, BigDecimal comMob) {
        BigDecimal valor = mobiliado ? comMob : semMob;
        BigDecimal repasse = repasses.findFirstByContratoFaixaId(f.getId())
                .map(r -> mobiliado ? r.getValorRepasseComMob() : r.getValorRepasseSemMob())
                .orElse(null);
        return new Preco(valor, repasse, f.getId());
    }

    /**
     * Preço B2C (Página 3/4 do prompt "4 telas B2C") — biletagem global por b2c_service,
     * sem imobiliária/contrato. Sem repasse (não existe no B2C).
     *
     * @param valorAluguel obrigatório no modo percentual
     * @param mobiliado    da solicitação; {@code null} tratado como {@code false}
     */
    @Transactional(readOnly = true)
    public Preco resolverB2c(UUID b2cServiceId, BigDecimal metragem,
                             BigDecimal valorAluguel, Boolean mobiliado) {
        boolean mob = Boolean.TRUE.equals(mobiliado);
        BiletagemCliente.Modo modo = biletagemCliente.findById(Boolean.TRUE)
                .map(BiletagemCliente::getModo)
                .orElseThrow(() -> new NotFoundException("Biletagem cliente nao configurada"));

        if (modo == BiletagemCliente.Modo.percentual) {
            if (valorAluguel == null) throw new IllegalArgumentException("Valor do aluguel obrigatorio");
            BiletagemClientePercentual p = biletagemClientePercentual.findById(b2cServiceId)
                    .orElseThrow(() -> new NotFoundException("Percentual nao configurado para " + b2cServiceId));
            BigDecimal valor = valorAluguel.multiply(p.getPercentual())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (p.getMinimo() != null && valor.compareTo(p.getMinimo()) < 0)
                valor = p.getMinimo().setScale(2, RoundingMode.HALF_UP);
            return new Preco(valor, null, null);
        }

        if (metragem == null) throw new IllegalArgumentException("Metragem obrigatoria");
        FaixaReferencia faixaRef = faixaReferencia.findAllByOrderByOrdemAsc().stream()
                .filter(fr -> metragem.compareTo(fr.getMetragemMin()) >= 0
                        && (fr.getMetragemMax() == null || metragem.compareTo(fr.getMetragemMax()) <= 0))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Metragem " + metragem + " fora das faixas de referencia"));

        BiletagemClienteFaixa f = biletagemClienteFaixa
                .findByB2cServiceIdAndFaixaReferenciaId(b2cServiceId, faixaRef.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Faixa nao configurada para " + b2cServiceId + " / " + faixaRef.getId()));
        BigDecimal valor = mob ? f.getValorComMob() : f.getValorSemMob();
        return new Preco(valor, null, f.getId());
    }
}
