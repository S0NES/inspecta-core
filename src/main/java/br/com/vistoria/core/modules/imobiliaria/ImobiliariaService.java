package br.com.vistoria.core.modules.imobiliaria;

import br.com.vistoria.core.config.TenantContext;
import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ServicoRepository;
import br.com.vistoria.core.modules.contrato.Contrato;
import br.com.vistoria.core.modules.contrato.ContratoFaixa;
import br.com.vistoria.core.modules.contrato.ContratoRepositories.ContratoRepository;
import br.com.vistoria.core.modules.contrato.ContratoRepositories.ContratoServicoGrupoRepository;
import br.com.vistoria.core.modules.contrato.ContratoRepositories.FaixaRepository;
import br.com.vistoria.core.modules.contrato.ContratoServicoGrupo;
import br.com.vistoria.core.modules.imobiliaria.dto.CreateImobiliariaRequest;
import br.com.vistoria.core.modules.imobiliaria.dto.ImobiliariaResponse;
import br.com.vistoria.core.modules.integracao.IntegracaoSyncService;
import br.com.vistoria.core.shared.exception.CnpjDuplicadoException;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import br.com.vistoria.core.shared.validation.PessoaDocumento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImobiliariaService {

    private final ImobiliariaRepository repository;
    private final IntegracaoSyncService integracaoSync;
    private final OutboxService outbox;
    private final ContratoRepository contratos;
    private final ContratoServicoGrupoRepository grupos;
    private final FaixaRepository faixas;
    private final ServicoRepository servicos;

    @Transactional
    public ImobiliariaResponse create(CreateImobiliariaRequest req) {
        String cnpj = PessoaDocumento.requireCnpj(req.cnpj());   // só dígitos, DV válido
        if (repository.existsByCnpj(cnpj)) {
            throw new CnpjDuplicadoException();
        }
        Imobiliaria i = new Imobiliaria();
        i.setRazaoSocial(req.razaoSocial());
        i.setNomeFantasia(req.nomeFantasia());
        i.setCnpj(cnpj);
        i.setPix(req.pix());
        i.setEmailFinanceiro(req.emailFinanceiro() == null || req.emailFinanceiro().isBlank()
                ? req.emailFinanceiro() : PessoaDocumento.requireEmail(req.emailFinanceiro()));
        i.setTelefone(req.telefone() == null || req.telefone().isBlank()
                ? req.telefone() : PessoaDocumento.requireTelefone(req.telefone()));
        i.setCep(req.cep());
        i.setEndereco(req.endereco());
        i.setNumero(req.numero());
        i.setComplemento(req.complemento());
        i.setCidadeId(req.cidadeId());
        Imobiliaria salvo = repository.save(i);
        vincularOuEspelhar("imobiliaria", salvo.getId(), req.plenoIdExterno());
        persistirContrato(salvo.getId(), req.contrato());

        // Customer no provider de pagamento — assíncrono (inspecta-worker grava external_payment_id).
        // Não bloqueia o cadastro: se o provider falhar, a imobiliária fica sem customer e o
        // checkout é criado sem customerId até um novo disparo.
        Map<String, Object> customer = new HashMap<>();
        customer.put("eventId", "customer-create-" + salvo.getId());
        customer.put("imobiliariaId", salvo.getId());
        customer.put("email", salvo.getEmailFinanceiro() == null ? "" : salvo.getEmailFinanceiro());
        customer.put("name", salvo.getRazaoSocial() == null ? "" : salvo.getRazaoSocial());
        customer.put("taxId", salvo.getCnpj() == null ? "" : salvo.getCnpj().replaceAll("\\D", ""));
        customer.put("cellphone", salvo.getTelefone() == null ? "" : salvo.getTelefone());
        outbox.publish(EventTypes.PAYMENT_CUSTOMER_CREATE, customer);

        return ImobiliariaResponse.from(salvo);
    }

    /** Grava o id do customer devolvido pelo provider de pagamento (chamado pelo inspecta-worker). */
    @Transactional
    public void registrarClientePagamento(UUID id, String externalPaymentId) {
        Imobiliaria i = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Imobiliaria " + id + " nao encontrada"));
        i.setExternalPaymentId(externalPaymentId);
    }

    /**
     * Exclui a imobiliária. Antes de remover a linha, publica {@code jobs.payment.customer-delete}
     * (quando há {@code external_payment_id}) para o inspecta-worker apagar o customer no provider.
     */
    @Transactional
    public void delete(UUID id) {
        Imobiliaria i = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Imobiliaria " + id + " nao encontrada"));
        String externalPaymentId = i.getExternalPaymentId();
        if (externalPaymentId != null && !externalPaymentId.isBlank()) {
            outbox.publish(EventTypes.PAYMENT_CUSTOMER_DELETE, Map.of(
                    "eventId", "customer-delete-" + id,
                    "imobiliariaId", id,
                    "externalPaymentId", externalPaymentId));
        }
        repository.delete(i);
    }

    /**
     * Persiste a biletagem enviada no POST de cadastro (mesmo modelo do
     * {@code PUT /v1/imobiliarias/{id}/contrato}). Sem {@code contrato} no body, não cria linha.
     */
    private void persistirContrato(UUID imobiliariaId, CreateImobiliariaRequest.ContratoCreateRequest body) {
        if (body == null) return;
        Contrato.Modo modo = parseModo(body.modo());
        if (modo == null) throw new IllegalArgumentException("modo obrigatorio (faixa|percentual)");
        Contrato c = new Contrato();
        c.setImobiliariaId(imobiliariaId);
        c.setStatus("ativo");
        c.setModo(modo);
        c.setPercentual(body.percentual());
        c.setPercentualMinimo(body.percentualMinimo());
        contratos.save(c);

        if (body.grupos() != null) {
            for (CreateImobiliariaRequest.GrupoItem it : body.grupos()) {
                if (it.servicoId() == null) throw new IllegalArgumentException("servicoId obrigatorio em cada grupo");
                ContratoServicoGrupo g = new ContratoServicoGrupo();
                g.setContratoId(c.getId());
                g.setServicoId(it.servicoId());
                g.setPercentual(it.percentual());
                g.setPercentualMinimo(it.percentualMinimo());
                grupos.save(g);
            }
        }

        if (modo == Contrato.Modo.faixa && body.faixas() != null) {
            Set<String> vistos = new HashSet<>();
            for (CreateImobiliariaRequest.FaixaItem it : body.faixas()) {
                if (it.servicoId() == null || it.faixaId() == null
                        || it.valorSemMobilia() == null || it.valorComMobilia() == null)
                    throw new IllegalArgumentException(
                            "servicoId, faixaId, valorSemMobilia e valorComMobilia sao obrigatorios");
                if (it.faixaId() < 1 || it.faixaId() > 5)
                    throw new IllegalArgumentException("faixaId deve estar entre 1 e 5 (faixa_referencia.id)");
                if (it.valorSemMobilia().signum() < 0 || it.valorComMobilia().signum() < 0
                        || (it.paStepMetragem() != null && it.paStepMetragem().signum() < 0)
                        || (it.paStepValor() != null && it.paStepValor().signum() < 0))
                    throw new IllegalArgumentException("valores nao podem ser negativos");
                if (!servicos.existsById(it.servicoId()))
                    throw new NotFoundException("Servico " + it.servicoId() + " nao encontrado");
                if (!vistos.add(it.servicoId() + ":" + it.faixaId()))
                    throw new IllegalArgumentException(
                            "faixa duplicada no corpo para (servicoId=" + it.servicoId() + ", faixaId=" + it.faixaId() + ")");
                ContratoFaixa f = new ContratoFaixa();
                f.setContratoId(c.getId());
                f.setServicoId(it.servicoId());
                f.setFaixaReferenciaId(it.faixaId());
                f.setValorSemMob(it.valorSemMobilia());
                f.setValorComMob(it.valorComMobilia());
                f.setPaStepMetragem(it.paStepMetragem());
                f.setPaStepValor(it.paStepValor());
                faixas.save(f);
            }
        }
    }

    private static Contrato.Modo parseModo(String modo) {
        if (modo == null || modo.isBlank()) return null;
        try {
            return Contrato.Modo.valueOf(modo.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("modo invalido: " + modo + " (use faixa|percentual)");
        }
    }

    /** cadastro externo (ID informado) -> vincula; senao -> enfileira espelhamento assincrono. */
    private void vincularOuEspelhar(String recursoTipo, UUID recursoId, String plenoIdExterno) {
        if (plenoIdExterno != null && !plenoIdExterno.isBlank()) {
            integracaoSync.vincularExterno(recursoTipo, recursoId, plenoIdExterno.trim());
        } else {
            integracaoSync.enfileirar(recursoTipo, recursoId);
        }
    }

    @Transactional(readOnly = true)
    public Page<ImobiliariaResponse> list(Pageable pageable) {
        List<UUID> tenants = TenantContext.getAll();
        Page<Imobiliaria> page = tenants.isEmpty()
                ? repository.findAll(pageable)
                : repository.findAllByIdIn(tenants, pageable);
        return page.map(ImobiliariaResponse::from);
    }

    /**
     * Sentinela "limpar vínculo" para {@link #update}: com bigint dava pra usar {@code <= 0}
     * (nunca um id real); UUID não tem um "inválido" natural, então usa o UUID nulo (nunca
     * gerado por {@code @UuidGenerator}/{@code gen_random_uuid()}).
     */
    private static final UUID PDF_TEMPLATE_LIMPAR = new UUID(0L, 0L);

    /**
     * Atualização parcial (P1). Só sobrescreve o que veio != null.
     *
     * <p>{@code pdfTemplateId}: {@code null} = não mexe; {@link #PDF_TEMPLATE_LIMPAR} = limpa o
     * vínculo (volta à resolução por chave); qualquer outro valor = vincula essa linha de
     * {@code pdf_templates}.</p>
     */
    @Transactional
    public ImobiliariaResponse update(UUID id, String razaoSocial, String nomeFantasia,
                                      String pix, String emailFinanceiro, String telefone,
                                      String cep, String endereco, String numero, String complemento,
                                      UUID cidadeId, Boolean ativa,
                                      UUID pdfTemplateId) {
        Imobiliaria i = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Imobiliaria " + id + " nao encontrada"));
        if (razaoSocial != null && !razaoSocial.isBlank()) i.setRazaoSocial(razaoSocial.trim());
        if (nomeFantasia != null) i.setNomeFantasia(nomeFantasia.isBlank() ? null : nomeFantasia.trim());
        if (pix != null) i.setPix(pix.isBlank() ? null : pix.trim());
        if (emailFinanceiro != null) i.setEmailFinanceiro(emailFinanceiro.isBlank() ? null : PessoaDocumento.requireEmail(emailFinanceiro));
        if (telefone != null) i.setTelefone(telefone.isBlank() ? null : PessoaDocumento.requireTelefone(telefone));
        if (cep != null) i.setCep(cep.isBlank() ? null : cep.trim());
        if (endereco != null) i.setEndereco(endereco.isBlank() ? null : endereco.trim());
        if (numero != null) i.setNumero(numero.isBlank() ? null : numero.trim());
        if (complemento != null) i.setComplemento(complemento.isBlank() ? null : complemento.trim());
        if (cidadeId != null) i.setCidadeId(cidadeId);
        if (ativa != null) i.setAtiva(ativa);
        if (pdfTemplateId != null) i.setPdfTemplateId(PDF_TEMPLATE_LIMPAR.equals(pdfTemplateId) ? null : pdfTemplateId);
        return ImobiliariaResponse.from(i);
    }

    /**
     * Vincula ou limpa <b>apenas</b> o template de laudo de uma imobiliária — os demais campos
     * ficam intactos. Endpoint dedicado para que SUPORTE possa fazer isso sem poder editar o
     * resto do cadastro (que continua exclusivo de ADMIN via {@link #update}).
     *
     * @param pdfTemplateId {@code null} limpa o vínculo (volta à resolução por chave / global);
     *                      qualquer outro valor vincula essa linha de {@code pdf_templates}.
     */
    @Transactional
    public ImobiliariaResponse definirPdfTemplate(UUID id, UUID pdfTemplateId) {
        UUID alvo = pdfTemplateId != null ? pdfTemplateId : PDF_TEMPLATE_LIMPAR;
        return update(id, null, null, null, null, null, null, null, null, null, null, null, alvo);
    }

    @Transactional(readOnly = true)
    public ImobiliariaResponse findById(UUID id) {
        return repository.findById(id)
                .map(ImobiliariaResponse::from)
                .orElseThrow(() -> new NotFoundException("Imobiliaria " + id + " nao encontrada"));
    }
}
