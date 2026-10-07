-- =====================================================================
-- V4 — Fluxo B2B (Imobiliária): atribuição imediata + faturamento pós-pago
-- Spec: specs/tasks/plano-b2b-billing.md, itens A1–A4.
--
-- Decisões (2026-09-09):
--   D1  solicitacoes.imobiliaria_id e cliente_id passam a ser opcionais
--       (já eram NULLABLE no V1__baseline — sem alteração de nullability aqui).
--   D2  aluguel/mobiliado deixam de ser atributo de verdade do imóvel para
--       preço; viram campos da solicitação (snapshot por solicitação).
--   D3  toda solicitação com imobiliária é pós-paga: atribuição publicada na
--       criação, e o valor entra num item de extrato liquidado depois via
--       cobrança consolidada (faturas_consolidadas).
-- =====================================================================

-- D2 — nível solicitação (um valor vale para todos os solicitacao_servicos
-- daquela solicitação). NULL nas linhas existentes; não usados retroativamente.
ALTER TABLE solicitacoes
    ADD COLUMN valor_aluguel NUMERIC(12,2),
    ADD COLUMN mobiliado     BOOLEAN;

-- Cobrança consolidada de um período (o "dono" do checkout no fluxo B2B,
-- no lugar da Solicitacao individual do fluxo reservado sem imobiliária).
CREATE TABLE faturas_consolidadas (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imobiliaria_id UUID NOT NULL REFERENCES imobiliarias(id),
    periodo_inicio DATE NOT NULL,
    periodo_fim    DATE NOT NULL,
    valor_total    NUMERIC(12,2) NOT NULL,
    status         TEXT NOT NULL DEFAULT 'pendente'
                   CHECK (status IN ('pendente', 'checkout_ready', 'pago', 'erro')),
    checkout_id    TEXT,
    checkout_url   TEXT,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Um item de extrato = o preço snapshot de um solicitacao_servico B2B no
-- momento da criação. fatura_id NULL = ainda em aberto.
CREATE TABLE extrato_itens (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imobiliaria_id         UUID NOT NULL REFERENCES imobiliarias(id),
    solicitacao_servico_id UUID NOT NULL REFERENCES solicitacao_servicos(id),
    valor                  NUMERIC(12,2) NOT NULL,
    criado_em              TIMESTAMPTZ NOT NULL DEFAULT now(),
    fatura_id              UUID REFERENCES faturas_consolidadas(id)
);
CREATE INDEX idx_extrato_itens_imob_criado ON extrato_itens (imobiliaria_id, criado_em);

-- =====================================================================
-- RLS — as duas tabelas carregam imobiliaria_id próprio: mesmo padrão
-- direto de clientes (tenant puro).
-- =====================================================================

ALTER TABLE faturas_consolidadas ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturas_consolidadas FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON faturas_consolidadas
    USING (
        public.current_tenant_ids() IS NULL
        OR imobiliaria_id = ANY (public.current_tenant_ids())
    );

ALTER TABLE extrato_itens ENABLE ROW LEVEL SECURITY;
ALTER TABLE extrato_itens FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON extrato_itens
    USING (
        public.current_tenant_ids() IS NULL
        OR imobiliaria_id = ANY (public.current_tenant_ids())
    );
