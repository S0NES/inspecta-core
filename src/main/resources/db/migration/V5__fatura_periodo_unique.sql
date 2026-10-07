-- =====================================================================
-- V5 — Uma fatura consolidada por imobiliária + período.
-- Dois POST /extrato/faturas concorrentes no mesmo recorte criavam duas
-- faturas e republicavam PAYMENT_FATURA_CHARGE_REQUESTED (cobrança dobrada).
-- O lock pessimista em findAbertos cobre janelas sobrepostas; este unique
-- cobre o recorte idêntico quando os dois INSERTs passam do SELECT.
-- =====================================================================

ALTER TABLE faturas_consolidadas
    ADD CONSTRAINT uk_faturas_consolidadas_imob_periodo
        UNIQUE (imobiliaria_id, periodo_inicio, periodo_fim);
