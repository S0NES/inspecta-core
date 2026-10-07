package br.com.vistoria.core.modules.cadastro;

import br.com.vistoria.core.config.TenantContext;
import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ImovelRepository;
import br.com.vistoria.core.modules.integracao.IntegracaoSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CadastroService {

    private final ImovelRepository imoveis;
    private final IntegracaoSyncService integracaoSync;

    @Transactional
    public Imovel criarImovel(Imovel imovel) {
        resolverImobiliaria(imovel);
        Imovel salvo = imoveis.save(imovel);
        // cadastro externo (imo_codigo informado) -> vincula; senao -> espelhamento assincrono.
        String externo = imovel.getPlenoIdExterno();
        if (externo != null && !externo.isBlank()) {
            integracaoSync.vincularExterno("imovel", salvo.getId(), externo.trim());
        } else {
            integracaoSync.enfileirar("imovel", salvo.getId());
        }
        return salvo;
    }

    /**
     * Vínculo imóvel → imobiliária (B4). Se o corpo não trouxer, herda do tenant
     * quando há exatamente um vínculo. Com múltiplos vínculos (ou admin/suporte,
     * sem tenant) o corpo deve informar `imobiliariaId`. Informado, valida
     * pertencimento ao conjunto de vínculos do usuário.
     */
    private void resolverImobiliaria(Imovel imovel) {
        if (imovel == null) return; // deixa imoveis.save(null) reclamar, como antes
        List<UUID> tenants = TenantContext.getAll();
        if (imovel.getImobiliariaId() == null) {
            if (tenants.size() == 1) {
                imovel.setImobiliariaId(tenants.get(0));
            } else if (tenants.size() > 1) {
                // Vários vínculos: ambíguo, o corpo tem que dizer qual.
                throw new IllegalArgumentException(
                        "imobiliariaId obrigatorio: voce atende " + tenants.size() + " imobiliarias");
            }
            // tenants vazio (admin/suporte): imóvel sem imobiliária é permitido no cadastro,
            // mas não gera solicitação (SolicitacaoService.criar exige imovel.imobiliaria_id).
        } else if (!tenants.isEmpty() && !tenants.contains(imovel.getImobiliariaId())) {
            throw new IllegalStateException("imobiliaria fora dos seus vinculos");
        }
    }
}
