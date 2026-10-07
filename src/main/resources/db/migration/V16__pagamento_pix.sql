-- PIX transparente (AbacatePay /transparents/create): a página de pagamento do B2C mostra o QR Code
-- da própria Inspecta em vez de redirecionar para o checkout hospedado. O pagamento continua uma linha
-- só por solicitação; o PIX convive com o checkout hospedado (cartão) na mesma linha.
ALTER TABLE pagamentos
    ADD COLUMN pix_id        TEXT,
    ADD COLUMN pix_br_code   TEXT,
    ADD COLUMN pix_qr_base64 TEXT,
    ADD COLUMN pix_expira_em TIMESTAMPTZ;

CREATE INDEX idx_pagamentos_pix_id ON pagamentos (pix_id) WHERE pix_id IS NOT NULL;
