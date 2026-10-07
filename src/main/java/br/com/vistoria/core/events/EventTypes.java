package br.com.vistoria.core.events;

public final class EventTypes {
    private EventTypes() {}

    public static final String USER_INVITED             = "notify.requested.user-invited";
    public static final String PASSWORD_RESET_REQUESTED = "notify.requested.password-reset-code";
    public static final String NOTIFY_PREFIX            = "notify.requested.";

    public static final String ASSIGNMENT_REQUESTED     = "jobs.assignment.requested";
    public static final String ASSIGNMENT_RETRY         = "jobs.assignment.retry";
    public static final String PDF_REQUESTED            = "jobs.pdf.laudo-requested";
    public static final String IMAGE_UPLOADED           = "jobs.image.process";
    public static final String ARQUIVO_UPLOADED         = "jobs.storage.upload-requested";
    public static final String PLENO_SYNC_REQUESTED     = "jobs.pleno.sync";
    public static final String SIGNATURE_REQUESTED      = "jobs.signature.send";
    public static final String SIGNATURE_CREATE_DOCUMENT = "jobs.signature.create-document";
    public static final String SIGNATURE_WEBHOOK        = "jobs.signature.webhook-received";
    public static final String PAYMENT_CHARGE_REQUESTED = "jobs.payment.charge-requested";
    /** Cobrança consolidada B2B: 1 checkout para o total de uma fatura_consolidada (V4). */
    public static final String PAYMENT_FATURA_CHARGE_REQUESTED = "jobs.payment.fatura-charge-requested";
    public static final String PAYMENT_PIX_REQUESTED    = "jobs.payment.pix-requested";
    public static final String PAYMENT_WEBHOOK          = "jobs.payment.webhook-received";
    public static final String PAYMENT_CHECKOUT_READY   = "jobs.payment.checkout-ready";
    public static final String PAYMENT_CUSTOMER_CREATE  = "jobs.payment.customer-create";
    public static final String PAYMENT_CUSTOMER_DELETE  = "jobs.payment.customer-delete";

    public static final String METRICS_ENVIADO         = "jobs.metrics.enviado";
    public static final String METRICS_STATUS          = "jobs.metrics.status";
    public static final String METRICS_PREFIX          = "jobs.metrics.";
}
