package br.com.vistoria.core.modules.notify;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Catálogo das utilidades transacionais de e-mail — o "para que serve" de cada template.
 *
 * <p>Cada utilidade tem um {@code uuidPadrao} (o UUID histórico de {@link NotifyTemplates} que
 * os publishers mandam no evento) e um rótulo. A ligação editável utilidade → template vive em
 * {@code notify.template_utilidades}; {@link NotifyService#template} consulta essa tabela e, se
 * houver uma linha para o {@code uuidPadrao} recebido, redireciona para o template configurado.
 * Sem linha (não deveria — é semeado em {@code V33}), usa o próprio {@code uuidPadrao}.</p>
 *
 * <p>{@code variaveis} lista os campos que o evento daquela utilidade entrega — é o que a tela
 * mostra ("este e-mail pode usar {{codigo}}") e a base de uma futura validação no save.</p>
 */
public enum TemplateUtilidade {

    password_reset("Redefinição de senha", NotifyTemplates.PASSWORD_RESET_CODE,
            List.of("codigo")),
    user_invited("Convite de usuário", NotifyTemplates.USER_INVITED,
            List.of("nome", "link")),
    api_key_expiring("API key expirando", NotifyTemplates.API_KEY_EXPIRING,
            List.of("prefix", "expiraEm")),
    laudo_assinado("Laudo assinado", NotifyTemplates.LAUDO_ASSINADO,
            List.of("nome", "laudoId")),
    pagamento_confirmado("Pagamento confirmado", NotifyTemplates.PAGAMENTO_CONFIRMADO,
            List.of("valor", "solicitacaoId")),
    pagamento_link("Link de pagamento", NotifyTemplates.PAGAMENTO_LINK,
            List.of("valor", "checkoutUrl")),
    vistoriador_pleno_criado("Acesso de vistoriador na Pleno", NotifyTemplates.VISTORIADOR_PLENO_CRIADO,
            List.of("nome", "portalUrl")),
    assinatura_link("Link de assinatura", NotifyTemplates.ASSINATURA_LINK,
            List.of("nome", "signingUrl")),
    assinatura_lembrete("Lembrete de assinatura", NotifyTemplates.ASSINATURA_LEMBRETE,
            List.of("nome", "signingUrl"));

    private final String rotulo;
    private final String uuidPadrao;
    private final List<String> variaveis;

    TemplateUtilidade(String rotulo, String uuidPadrao, List<String> variaveis) {
        this.rotulo = rotulo;
        this.uuidPadrao = uuidPadrao;
        this.variaveis = List.copyOf(variaveis);
    }

    public String rotulo() {
        return rotulo;
    }

    public String uuidPadrao() {
        return uuidPadrao;
    }

    public List<String> variaveis() {
        return variaveis;
    }

    public static Optional<TemplateUtilidade> porNome(String nome) {
        return Arrays.stream(values()).filter(u -> u.name().equals(nome)).findFirst();
    }

    /** A utilidade cujo template padrão é este UUID, se houver. */
    public static Optional<TemplateUtilidade> porUuidPadrao(String uuid) {
        return Arrays.stream(values()).filter(u -> u.uuidPadrao.equalsIgnoreCase(uuid)).findFirst();
    }
}
