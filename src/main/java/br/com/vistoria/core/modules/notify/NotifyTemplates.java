package br.com.vistoria.core.modules.notify;

/**
 * UUIDs fixos dos templates de mensagem transacionais.
 *
 * <p>São constantes de sistema, não configuração por ambiente: são o identificador lógico
 * estável de cada template transacional. Os publishers usam estas constantes no lugar dos
 * antigos slugs ({@code "user-invited"} etc.), e o {@link NotifyService#template} resolve o
 * UUID contra {@code notify.message_templates}.</p>
 *
 * <p>O sistema NÃO semeia estes templates — o ADMIN os cria na tela de Templates e os
 * vincula às utilidades na tela de Utilidades. Até lá, o envio daquele tipo de mensagem
 * falha ("Template nao encontrado").</p>
 *
 * <p>Editar o texto de um destes templates cria uma nova versão sob o mesmo UUID — a
 * resolução sempre pega a maior versão ativa, então o publisher não muda.</p>
 */
public final class NotifyTemplates {

    private NotifyTemplates() {}

    public static final String USER_INVITED             = "00000000-0000-4000-8000-000000000001";
    public static final String PASSWORD_RESET_CODE      = "00000000-0000-4000-8000-000000000002";
    public static final String API_KEY_EXPIRING         = "00000000-0000-4000-8000-000000000003";
    public static final String LAUDO_ASSINADO           = "00000000-0000-4000-8000-000000000004";
    public static final String PAGAMENTO_CONFIRMADO     = "00000000-0000-4000-8000-000000000005";
    public static final String PAGAMENTO_LINK           = "00000000-0000-4000-8000-000000000006";
    public static final String VISTORIADOR_PLENO_CRIADO = "00000000-0000-4000-8000-000000000007";
    public static final String ASSINATURA_LINK          = "00000000-0000-4000-8000-000000000008";
    public static final String ASSINATURA_LEMBRETE      = "00000000-0000-4000-8000-000000000009";
}
