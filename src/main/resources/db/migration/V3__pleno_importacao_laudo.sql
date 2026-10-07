-- =====================================================================
-- V3 — Importação de vistorias concluídas na Pleno (download PDF → assinatura)
-- Spec: PLENO_INTEGRATION_PROMPT.md §4 e §6.1.
--
-- A "volta" do ciclo Pleno: depois que uma solicitação já foi espelhada
-- (escrita, feita), acompanhar o andamento NA Pleno até a vistoria
-- terminar, baixar o PDF e entregá-lo ao pipeline de laudo/assinatura que
-- já existe. Nada da API Pleno é chamado daqui — isso vive no inspecta-worker;
-- estas tabelas só guardam o estado operacional do acompanhamento.
--
-- RLS: as duas tabelas novas são operacionais (equipe Inspecta/inspecta-worker),
-- sem policy por tenant — mesmo padrão de integracao_sync / *_checkpoint.
-- =====================================================================

-- Origem do laudo: gerado internamente (pipeline OpenHTML2PDF) vs. PDF já
-- pronto baixado da Pleno. Um laudo nunca passa pelos dois caminhos.
ALTER TABLE laudos
    ADD COLUMN origem VARCHAR(20) NOT NULL DEFAULT 'gerado_internamente'
        CHECK (origem IN ('gerado_internamente', 'importado_pleno'));

CREATE TABLE pleno_importacao_vistoria (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    solicitacao_servico_id      UUID NOT NULL UNIQUE REFERENCES solicitacao_servicos(id),
    sta_sol_codigo_atual        INTEGER,
    status_importacao           VARCHAR(30) NOT NULL DEFAULT 'SOLICITADA',
        -- SOLICITADA | EM_ANDAMENTO | FINALIZADA_SEM_PDF | FINALIZADA_COM_PDF
        -- | PDF_BAIXADO | ENVIADA_PARA_ASSINATURA | CANCELADA | ERRO
    tentativas_verificacao      INTEGER     NOT NULL DEFAULT 0,
    ultima_verificacao_em       TIMESTAMPTZ,
    proxima_verificacao_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    link_pdf_externo            TEXT,
    pdf_arquivo_id              UUID REFERENCES arquivos(id),
    pdf_sha256                  VARCHAR(64),
    laudo_id                    UUID REFERENCES laudos(id),
    enviado_para_assinatura_em  TIMESTAMPTZ,
    erro_mensagem               TEXT,
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em               TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Varredura do poller (PlenoImportacaoService.elegiveis): linhas cuja
-- próxima verificação já venceu.
CREATE INDEX idx_pleno_importacao_proxima_verificacao
    ON pleno_importacao_vistoria (proxima_verificacao_em);

CREATE TABLE pleno_importacao_checkpoint (
    id                SMALLINT PRIMARY KEY DEFAULT 1,
    ultimo_checkpoint TIMESTAMPTZ NOT NULL,
    CHECK (id = 1)
);
