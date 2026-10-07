-- =====================================================================
-- BASELINE — schema consolidado, um único arquivo.
--
-- DB fresh após este V1 == JPA atual: PKs/FKs UUID, todas as colunas que
-- foram V3–V7, tabelas ambientes/fotos, interveniente_papeis e
-- solicitacao_intervenientes.
-- Sem V2..V7 separados — foram dobrados aqui. Os seeds idempotentes ficam
-- no fim do arquivo.
--
-- `gen_random_uuid()` no DEFAULT é só rede de segurança para inserts fora do
-- JPA (seed de CI, este arquivo). O gerador oficial de PK é o Hibernate
-- (@UuidGenerator(style = TIME), UUIDv7 time-ordered).
--
-- Ambientes que já aplicaram um V1..V7 anterior: como este arquivo tem o
-- mesmo número de versão mas conteúdo diferente, recriar do zero —
--   DROP SCHEMA public CASCADE; DROP SCHEMA notify CASCADE; DROP SCHEMA metrics CASCADE;
--   CREATE SCHEMA public;  TRUNCATE flyway_schema_history;  -- (ou drop do schema do Flyway)
-- e deixar o inspecta-api rodar a migração de novo. Pré-lançamento, prod ~vazio
-- (memória prod-dados-referencia-vazios) — sem expand-contract.
-- =====================================================================


-- ============ INFRA / OUTBOX ============

CREATE TABLE imobiliarias (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    razao_social     TEXT NOT NULL,
    nome_fantasia    TEXT,
    cnpj             VARCHAR(18) NOT NULL UNIQUE,
    pix              TEXT,
    email_financeiro TEXT,
    telefone         TEXT,
    external_payment_id VARCHAR,
    ativa            BOOLEAN NOT NULL DEFAULT TRUE,
    pdf_template_id  UUID,   -- FK adicionada após pdf_templates (forward ref)
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_eventos (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    routing_key   TEXT NOT NULL,
    payload       JSONB NOT NULL,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    publicado_em  TIMESTAMPTZ,
    tentativas    INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_pending ON outbox_eventos (criado_em) WHERE publicado_em IS NULL;

CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);


-- ============ TENANCY & IDENTIDADE ============

CREATE TABLE usuarios (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imobiliaria_id UUID REFERENCES imobiliarias(id),
    nome           TEXT,
    email          TEXT NOT NULL UNIQUE,
    senha_hash     TEXT,
    perfil         TEXT NOT NULL CHECK (perfil IN ('admin','suporte','imob_gerencia','imob_operacao','vistoriador','cliente')),
    status         TEXT NOT NULL DEFAULT 'convidado' CHECK (status IN ('convidado','ativo','inativo')),
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo       TEXT NOT NULL CHECK (tipo IN ('refresh','invite','api_key','reset_senha')),
    prefix     TEXT,
    hash       TEXT NOT NULL,
    escopos    TEXT[] NOT NULL DEFAULT '{}',
    expira_em  TIMESTAMPTZ NOT NULL,
    revogado   BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tokens_prefix ON tokens (prefix) WHERE tipo = 'api_key' AND NOT revogado;
CREATE INDEX idx_tokens_hash_tipo ON tokens (hash, tipo);

-- clientes: cada cliente pertence a exatamente UMA imobiliária (imobiliaria_id).
CREATE TABLE clientes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imobiliaria_id   UUID REFERENCES imobiliarias(id),
    usuario_id       UUID REFERENCES usuarios(id),
    nome             TEXT NOT NULL,
    cpf_cnpj         TEXT,
    email            TEXT,
    telefone         TEXT,
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_clientes_tenant ON clientes (imobiliaria_id);

CREATE TABLE idempotency_keys (
    chave         TEXT        PRIMARY KEY,
    metodo        TEXT        NOT NULL,
    path          TEXT        NOT NULL,
    request_hash  TEXT        NOT NULL,
    status_http   INT,
    response_body TEXT,
    tenant_id     UUID,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_em     TIMESTAMPTZ NOT NULL
);
CREATE INDEX idempotency_keys_expira_em_ix ON idempotency_keys (expira_em);

CREATE TABLE usuario_imobiliarias (
    usuario_id     UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    imobiliaria_id UUID NOT NULL REFERENCES imobiliarias(id) ON DELETE CASCADE,
    PRIMARY KEY (usuario_id, imobiliaria_id)
);
CREATE INDEX idx_usuario_imob_imob ON usuario_imobiliarias (imobiliaria_id);


-- ============ CADASTROS ============

CREATE TABLE cidades (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome TEXT NOT NULL,
    uf   VARCHAR(2) NOT NULL,
    lat  DOUBLE PRECISION,
    lng  DOUBLE PRECISION,
    UNIQUE (nome, uf)
);

CREATE TABLE imoveis (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imobiliaria_id UUID REFERENCES imobiliarias(id),
    cidade_id      UUID NOT NULL REFERENCES cidades(id),
    endereco       TEXT NOT NULL,             -- logradouro (V7)
    numero         TEXT,                      -- V7
    complemento    TEXT,                      -- V7
    cib            TEXT,                      -- V7
    observacoes    TEXT,                      -- V7
    cep            VARCHAR(9),
    lat            DOUBLE PRECISION,
    lng            DOUBLE PRECISION,
    metragem       NUMERIC(10,2) NOT NULL,
    mobiliado      BOOLEAN NOT NULL DEFAULT FALSE,
    tipo           TEXT,
    valor_aluguel  NUMERIC(12,2),             -- V3: base do cálculo percentual
    ativo          BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_imoveis_tenant ON imoveis (imobiliaria_id);

-- Papéis de interveniente — lookup editável SÓ pelo banco (sem CRUD HTTP).
-- Seed: locador, locatario, fiador. Sem 'testemunha'.
CREATE TABLE interveniente_papeis (
    slug      TEXT PRIMARY KEY,
    nome      TEXT NOT NULL,
    ativo     BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- intervenientes DO IMÓVEL (cadastro para Pleno). Não é a fonte da assinatura (Autentique) —
-- essa é solicitacao_intervenientes.
CREATE TABLE intervenientes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imovel_id  UUID NOT NULL REFERENCES imoveis(id),
    papel_slug TEXT NOT NULL REFERENCES interveniente_papeis(slug),
    nome       TEXT NOT NULL,
    cpf        TEXT,
    email      TEXT,
    telefone   TEXT
);
CREATE INDEX idx_intervenientes_imovel ON intervenientes (imovel_id);

CREATE TABLE ambientes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imovel_id     UUID NOT NULL REFERENCES imoveis(id),
    nome          TEXT NOT NULL,
    tipo          TEXT,
    ordem         INT NOT NULL DEFAULT 0,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ambientes_imovel ON ambientes (imovel_id);


-- ============ CATÁLOGO / COMERCIAL ============

CREATE TABLE tipos_servico (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome           TEXT NOT NULL UNIQUE,
    duracao_config JSONB NOT NULL DEFAULT '{}',
    ativo          BOOLEAN NOT NULL DEFAULT TRUE
);

-- V3: "Serviço" guarda-chuva (ex.: Elegance) agrupa N Tipos de Serviço (M:N).
CREATE TABLE servicos (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome      TEXT NOT NULL UNIQUE,
    ativo     BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE servico_tipos (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    servico_id           UUID NOT NULL REFERENCES servicos(id) ON DELETE CASCADE,
    tipo_servico_id      UUID NOT NULL REFERENCES tipos_servico(id) ON DELETE CASCADE,
    pleno_tip_vis_codigo INTEGER,   -- V5: código Pleno por combinação Serviço × Tipo
    UNIQUE (servico_id, tipo_servico_id)
);
CREATE INDEX idx_servico_tipos_servico ON servico_tipos (servico_id);
CREATE INDEX idx_servico_tipos_tipo    ON servico_tipos (tipo_servico_id);

-- V3: 5 faixas de metragem de referência, globais e fixas. metragem_max NULL = faixa 5.
CREATE TABLE faixa_referencia (
    id           SMALLINT PRIMARY KEY,
    ordem        SMALLINT NOT NULL UNIQUE,
    metragem_min NUMERIC(10,2) NOT NULL,
    metragem_max NUMERIC(10,2),
    rotulo       TEXT NOT NULL
);

CREATE TABLE contratos (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imobiliaria_id     UUID NOT NULL REFERENCES imobiliarias(id),
    modo               TEXT NOT NULL CHECK (modo IN ('faixa','percentual')),
    percentual         NUMERIC(5,2),
    percentual_minimo  NUMERIC(12,2),   -- V3
    volume_mensal      INT,
    faturamento_minimo NUMERIC(12,2),
    vigencia_inicio    DATE,
    vigencia_fim       DATE,
    status             TEXT NOT NULL DEFAULT 'ativo',
    criado_em          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT contratos_status_ck CHECK (status IN ('ativo', 'inativo'))
);
CREATE INDEX idx_contratos_tenant ON contratos (imobiliaria_id);

CREATE TABLE contrato_servicos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_id     UUID NOT NULL REFERENCES contratos(id),
    tipo_servico_id UUID NOT NULL REFERENCES tipos_servico(id),
    ativo           BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (contrato_id, tipo_servico_id)
);

-- V3: quais Serviços a imobiliária oferece + override percentual/piso por Serviço.
CREATE TABLE contrato_servico_grupos (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_id       UUID NOT NULL REFERENCES contratos(id) ON DELETE CASCADE,
    servico_id        UUID NOT NULL REFERENCES servicos(id),
    percentual        NUMERIC(5,2),
    percentual_minimo NUMERIC(12,2),
    UNIQUE (contrato_id, servico_id)
);
CREATE INDEX idx_contrato_grupos_contrato ON contrato_servico_grupos (contrato_id);

-- V4: no modo faixa os valores são por (Serviço guarda-chuva × faixa de referência).
-- tipo_servico_id / metragem_min / metragem_max ficam nullable (só legado os usa).
CREATE TABLE contrato_faixas (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_id         UUID NOT NULL REFERENCES contratos(id) ON DELETE CASCADE,
    tipo_servico_id     UUID REFERENCES tipos_servico(id),
    servico_id          UUID REFERENCES servicos(id),
    faixa_referencia_id SMALLINT REFERENCES faixa_referencia(id),
    metragem_min        NUMERIC(10,2),
    metragem_max        NUMERIC(10,2),
    valor_sem_mob       NUMERIC(12,2) NOT NULL,
    valor_com_mob       NUMERIC(12,2) NOT NULL,
    pa_step_metragem    NUMERIC(10,2),
    pa_step_valor       NUMERIC(12,2),
    revisao_pendente    BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_faixas_lookup ON contrato_faixas (contrato_id, tipo_servico_id, metragem_min, metragem_max);
CREATE UNIQUE INDEX ux_contrato_faixas_servico_faixa
    ON contrato_faixas (contrato_id, servico_id, faixa_referencia_id)
    WHERE servico_id IS NOT NULL;
CREATE INDEX idx_contrato_faixas_servico ON contrato_faixas (contrato_id, servico_id);

CREATE TABLE contrato_repasses (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_faixa_id      UUID NOT NULL REFERENCES contrato_faixas(id) ON DELETE CASCADE,
    valor_repasse_sem_mob  NUMERIC(12,2) NOT NULL,
    valor_repasse_com_mob  NUMERIC(12,2) NOT NULL
);
CREATE INDEX idx_contrato_repasses_faixa ON contrato_repasses (contrato_faixa_id);


-- ============ VISTORIADOR ============

CREATE TABLE vistoriadores (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id       UUID NOT NULL UNIQUE REFERENCES usuarios(id),
    cidades          UUID[] NOT NULL DEFAULT '{}',
    lat_base         DOUBLE PRECISION,
    lng_base         DOUBLE PRECISION,
    distancia_max_km NUMERIC(6,1),
    pleno_id         TEXT,
    dados_pagamento  JSONB NOT NULL DEFAULT '{}',
    ativo            BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE disponibilidades (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vistoriador_id UUID NOT NULL REFERENCES vistoriadores(id),
    dia_semana     INT NOT NULL CHECK (dia_semana BETWEEN 0 AND 6),
    hora_inicio    TIME NOT NULL,
    hora_fim       TIME NOT NULL,
    UNIQUE (vistoriador_id, dia_semana, hora_inicio)
);

CREATE TABLE disponibilidade_excecoes (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vistoriador_id UUID NOT NULL REFERENCES vistoriadores(id),
    tipo           TEXT NOT NULL CHECK (tipo IN ('ferias','bloqueio')),
    data_inicio    DATE NOT NULL,
    data_fim       DATE NOT NULL
);
CREATE INDEX idx_disponibilidade_excecoes ON disponibilidade_excecoes (vistoriador_id, data_inicio, data_fim);


-- ============ SOLICITAÇÃO ============

CREATE TABLE solicitacoes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imobiliaria_id   UUID REFERENCES imobiliarias(id),   -- sempre = imovel.imobiliaria_id em linha nova
    cliente_id       UUID REFERENCES clientes(id),       -- obrigatório na criação (o service exige)
    imovel_id        UUID NOT NULL REFERENCES imoveis(id),
    urgente          BOOLEAN NOT NULL DEFAULT FALSE,
    chaves_retirada  TEXT,
    chaves_devolucao TEXT,
    origem           TEXT NOT NULL CHECK (origem IN ('painel','portal','api')),
    criado_por       UUID REFERENCES usuarios(id),
    checkout_id      VARCHAR,
    checkout_url     VARCHAR,
    payment_status   VARCHAR NOT NULL DEFAULT 'nao_iniciado',
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_solicitacoes_tenant ON solicitacoes (imobiliaria_id);

-- Quem acompanha/assina a vistoria. N linhas ok (dois locadores ok). Create
-- rejeita 0. nome/cpf/email validados na camada de serviço (mesmo utils do inspecta-web).
CREATE TABLE solicitacao_intervenientes (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    solicitacao_id UUID NOT NULL REFERENCES solicitacoes(id) ON DELETE CASCADE,
    papel_slug     TEXT NOT NULL REFERENCES interveniente_papeis(slug),
    nome           TEXT NOT NULL,
    cpf            TEXT NOT NULL,   -- 11 dígitos na gravação
    email          TEXT NOT NULL,  -- trim + lower
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_solicitacao_intervenientes_solicitacao ON solicitacao_intervenientes (solicitacao_id);

CREATE TABLE solicitacao_servicos (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    solicitacao_id       UUID NOT NULL REFERENCES solicitacoes(id),
    tipo_servico_id      UUID NOT NULL REFERENCES tipos_servico(id),
    status               TEXT NOT NULL DEFAULT 'pendente'
                         CHECK (status IN ('pendente','atribuida','agendada','em_execucao','concluida','cancelada')),
    vistoriador_id       UUID REFERENCES vistoriadores(id),
    data_desejada        TIMESTAMPTZ,   -- V3
    data_agendada        TIMESTAMPTZ,
    duracao_estimada_min INT,
    product_id           UUID,
    external_product_id  VARCHAR,
    preco_snapshot       NUMERIC(12,2) NOT NULL,
    repasse_snapshot     NUMERIC(12,2),
    preco_manual         BOOLEAN NOT NULL DEFAULT FALSE,   -- legado; sem uso no fluxo atual
    criado_em            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_servicos_vistoriador_mes ON solicitacao_servicos (vistoriador_id, criado_em);
CREATE INDEX idx_solicitacao_servicos_solicitacao ON solicitacao_servicos (solicitacao_id);
CREATE INDEX idx_servicos_agenda ON solicitacao_servicos (vistoriador_id, data_agendada)
  WHERE status IN ('atribuida', 'agendada', 'em_execucao');

CREATE TABLE atribuicoes (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    servico_id     UUID NOT NULL REFERENCES solicitacao_servicos(id),
    vistoriador_id UUID NOT NULL REFERENCES vistoriadores(id),
    acao           TEXT NOT NULL CHECK (acao IN ('atribuido','reatribuido','reagendado')),
    motivo         TEXT,
    criado_por     TEXT NOT NULL DEFAULT 'sistema',
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- união discriminada: literal 'sistema' ou um usuario_id (UUID textual)
    CONSTRAINT atribuicoes_criado_por_ck CHECK (
        criado_por = 'sistema'
        OR criado_por ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
);
CREATE INDEX idx_atribuicoes_workload ON atribuicoes (vistoriador_id, criado_em);
CREATE INDEX idx_atribuicoes_servico ON atribuicoes (servico_id);

CREATE TABLE fila_atribuicao (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    servico_id        UUID NOT NULL UNIQUE REFERENCES solicitacao_servicos(id),
    tentativas        INT NOT NULL DEFAULT 0,
    proxima_tentativa TIMESTAMPTZ NOT NULL,
    criado_em         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_fila_atribuicao_proxima_tentativa ON fila_atribuicao (proxima_tentativa);


-- ============ LAUDO ============

CREATE TABLE arquivos (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    r2_key           TEXT NOT NULL UNIQUE,
    thumb_key        TEXT,
    tipo             TEXT,
    mime             TEXT,
    tamanho          BIGINT,
    owner_type       TEXT,
    owner_id         UUID,
    processado       BOOLEAN NOT NULL DEFAULT FALSE,
    conteudo         bytea,
    storage_provider varchar,
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fotos (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ambiente_id   UUID NOT NULL REFERENCES ambientes(id) ON DELETE CASCADE,
    arquivo_id    UUID NOT NULL REFERENCES arquivos(id),
    ordem         INT NOT NULL DEFAULT 0,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_fotos_ambiente ON fotos (ambiente_id);

CREATE TABLE laudos (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    solicitacao_servico_id  UUID NOT NULL REFERENCES solicitacao_servicos(id),
    versao                  INT NOT NULL DEFAULT 1,
    status                  TEXT NOT NULL DEFAULT 'rascunho'
                            CHECK (status IN ('rascunho','finalizado','assinado')),
    conteudo_json           JSONB NOT NULL DEFAULT '{}',
    pdf_arquivo_id          UUID REFERENCES arquivos(id),
    assinado_em             TIMESTAMPTZ,
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (solicitacao_servico_id, versao)
);

CREATE TABLE contestacoes (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    laudo_id  UUID NOT NULL REFERENCES laudos(id),
    node_uuid UUID NOT NULL,
    autor_id  UUID REFERENCES usuarios(id),
    texto     TEXT NOT NULL,
    status    TEXT NOT NULL DEFAULT 'aberta' CHECK (status IN ('aberta','aceita','rejeitada')),
    resolucao TEXT,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_contestacoes_laudo ON contestacoes (laudo_id);

CREATE TABLE reparos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contestacao_id  UUID REFERENCES contestacoes(id),
    solicitacao_id  UUID REFERENCES solicitacoes(id),
    descricao       TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'pendente',
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (contestacao_id IS NOT NULL OR solicitacao_id IS NOT NULL),
    CONSTRAINT reparos_status_ck CHECK (status IN ('pendente', 'realizado', 'cancelado'))
);
CREATE INDEX idx_reparos_solicitacao ON reparos (solicitacao_id) WHERE solicitacao_id IS NOT NULL;
CREATE INDEX idx_reparos_contestacao ON reparos (contestacao_id) WHERE contestacao_id IS NOT NULL;

CREATE TABLE signatarios (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    documento_tipo       TEXT NOT NULL,
    documento_id         UUID NOT NULL,
    nome                 TEXT NOT NULL,
    email                TEXT NOT NULL,
    papel                TEXT,
    status_assinatura    TEXT NOT NULL DEFAULT 'pendente',
    external_signer_id   TEXT,
    external_document_id TEXT,
    signing_url          TEXT,
    criado_em            TIMESTAMPTZ NOT NULL DEFAULT now(),
    assinado_em          TIMESTAMPTZ,
    lembrete_enviado_em  TIMESTAMPTZ,
    CONSTRAINT signatarios_status_assinatura_ck CHECK (status_assinatura IN ('pendente', 'assinado', 'recusado')),
    CONSTRAINT signatarios_documento_tipo_ck CHECK (documento_tipo IN ('laudo'))
);
CREATE INDEX idx_signatarios_documento ON signatarios (documento_tipo, documento_id);
CREATE INDEX idx_signatarios_external_document ON signatarios (external_document_id);
CREATE INDEX idx_signatarios_external_signer ON signatarios (external_signer_id);
-- Varredura do cron de lembrete (SignatureLembreteCron): assinaturas ainda pendentes por documento enviado.
CREATE INDEX idx_signatarios_pendente_lembrete ON signatarios (criado_em)
  WHERE status_assinatura = 'pendente' AND external_document_id IS NOT NULL;


-- ============ FINANCEIRO ============

CREATE TABLE pagamentos (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    solicitacao_id UUID NOT NULL REFERENCES solicitacoes(id),
    provider       TEXT NOT NULL,
    external_id    TEXT,
    valor          NUMERIC(12,2) NOT NULL,
    status         TEXT NOT NULL DEFAULT 'pendente',
    checkout_url   TEXT,
    event_id       TEXT UNIQUE,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pagamentos_status_ck CHECK (status IN ('criando', 'pendente', 'pago', 'falhou', 'estornado'))
);
CREATE INDEX idx_pagamentos_solicitacao ON pagamentos (solicitacao_id);
CREATE INDEX idx_pagamentos_external_id ON pagamentos (external_id);

CREATE TABLE pagamentos_vistoriador (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vistoriador_id         UUID NOT NULL REFERENCES vistoriadores(id),
    periodo                TEXT NOT NULL,
    valor                  NUMERIC(12,2) NOT NULL,
    status                 TEXT NOT NULL DEFAULT 'pendente',
    comprovante_arquivo_id UUID REFERENCES arquivos(id),
    criado_em              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pagamentos_vistoriador_status_ck CHECK (status IN ('pendente', 'pago'))
);


-- ============ INTEGRAÇÃO & ACESSO PÚBLICO ============

CREATE TABLE acessos_publicos (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash TEXT NOT NULL UNIQUE,
    tipo       TEXT NOT NULL CHECK (tipo IN ('laudo','imagens')),
    recurso_id UUID NOT NULL,
    expira_em  TIMESTAMPTZ NOT NULL,
    revogado   BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE integracao_sync (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sistema      TEXT NOT NULL DEFAULT 'pleno',
    recurso_tipo TEXT NOT NULL,
    recurso_id   UUID NOT NULL,
    external_id  TEXT,
    ultimo_sync  TIMESTAMPTZ,
    status       TEXT NOT NULL DEFAULT 'pendente',
    erro         TEXT,
    origem       TEXT NOT NULL DEFAULT 'espelho',
    UNIQUE (sistema, recurso_tipo, recurso_id),
    CONSTRAINT integracao_sync_sistema_ck CHECK (sistema IN ('pleno')),
    CONSTRAINT integracao_sync_status_ck  CHECK (status IN ('pendente', 'sincronizado', 'erro')),
    CONSTRAINT integracao_sync_origem_ck  CHECK (origem IN ('espelho', 'manual'))
);
CREATE INDEX idx_integracao_sync_status_id ON integracao_sync (status, id);

CREATE TABLE integracao_token_cache (
    chave     TEXT PRIMARY KEY,
    valor     TEXT NOT NULL,
    expira_em TIMESTAMP NOT NULL
);

CREATE TABLE webhooks_recebidos (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    origem      TEXT NOT NULL,
    event_id    TEXT NOT NULL UNIQUE,
    payload     JSONB NOT NULL,
    processado  BOOLEAN NOT NULL DEFAULT FALSE,
    recebido_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pleno_pessoa_garantia (
    imobiliaria_id UUID PRIMARY KEY REFERENCES imobiliarias(id) ON DELETE CASCADE,
    pes_codigo     text NOT NULL,
    cli_codigo     text NOT NULL,
    nome           text NOT NULL,
    cpf            text NOT NULL,
    email          text NOT NULL,
    telefone       text NOT NULL,
    criado_em      timestamptz NOT NULL DEFAULT now()
);


-- ============ PDF TEMPLATES ============

CREATE TABLE pdf_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chave           TEXT        NOT NULL,
    imobiliaria_id  UUID        REFERENCES imobiliarias(id) ON DELETE CASCADE,
    versao          INT         NOT NULL DEFAULT 1,
    nome            TEXT        NOT NULL,
    html            TEXT        NOT NULL,
    css             TEXT,
    ativo           BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE imobiliarias
    ADD CONSTRAINT imobiliarias_pdf_template_id_fkey
    FOREIGN KEY (pdf_template_id) REFERENCES pdf_templates(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_pdf_templates_imobiliaria
    ON pdf_templates (chave, imobiliaria_id, versao)
    WHERE imobiliaria_id IS NOT NULL;
CREATE UNIQUE INDEX uq_pdf_templates_global
    ON pdf_templates (chave, versao)
    WHERE imobiliaria_id IS NULL;
CREATE INDEX idx_pdf_templates_lookup
    ON pdf_templates (chave, imobiliaria_id, ativo, versao DESC);


-- ============ SCHEMA notify ============

CREATE SCHEMA IF NOT EXISTS notify;

CREATE TABLE notify.entregas (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  idempotency_key  TEXT NOT NULL UNIQUE,
  canal            TEXT NOT NULL,
  destinatario     TEXT NOT NULL,
  template_id      TEXT NOT NULL,
  tenant_id        UUID,
  status           TEXT NOT NULL DEFAULT 'enviando',
  external_id      TEXT,
  tentativas       INT NOT NULL DEFAULT 0,
  erro             TEXT,
  criado_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
  atualizado_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT notify_entregas_status_ck CHECK (status IN ('enviando', 'enviado', 'falhou'))
);
CREATE INDEX idx_entregas_external_id ON notify.entregas (external_id);

CREATE TABLE notify.webhook_eventos (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  broker      TEXT NOT NULL,
  event_id    TEXT NOT NULL,
  recebido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (broker, event_id)
);

-- Templates UUID-nativos (fonte de verdade). PK (uuid, versao); resolução = maior
-- versão ativa. Versão publicada nunca é editada.
CREATE TABLE notify.message_templates (
    uuid           UUID        NOT NULL,
    versao         INT         NOT NULL DEFAULT 1,
    canal          TEXT        NOT NULL,
    nome           TEXT        NOT NULL,
    assunto        TEXT,
    corpo          TEXT        NOT NULL,
    ativo          BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (uuid, versao)
);
CREATE INDEX idx_message_templates_lookup
    ON notify.message_templates (uuid, ativo, versao DESC);

-- Ligação editável utilidade -> template. PK em utilidade = no máx. 1 por utilidade.
CREATE TABLE notify.template_utilidades (
    utilidade      TEXT        NOT NULL PRIMARY KEY,
    template_id    UUID        NOT NULL,
    atualizado_por UUID,
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- ============ SCHEMA metrics ============

CREATE SCHEMA IF NOT EXISTS metrics;

CREATE TABLE metrics.envios (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  mensagem_id       TEXT        NOT NULL,
  tipo_envio        TEXT        NOT NULL,
  tipo_comunicacao  TEXT        NOT NULL,
  external_id       TEXT,
  status            TEXT        NOT NULL DEFAULT 'criado',
  status_raw        TEXT,
  broker            TEXT,
  data_criacao      TIMESTAMPTZ NOT NULL DEFAULT now(),
  data_envio        TIMESTAMPTZ,
  status_em         TIMESTAMPTZ,
  tenant_id         UUID,
  criado_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
  atualizado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT metrics_envios_mensagem_id_uk UNIQUE (mensagem_id),
  CONSTRAINT metrics_envios_status_ck CHECK (status IN
    ('criado','enviado','entregue','aberto','clicado','bounce','reclamacao','falha','desconhecido')),
  CONSTRAINT metrics_envios_tipo_comunicacao_ck CHECK (tipo_comunicacao IN
    ('conta','assinatura_laudo','operacional','marketing'))
);
CREATE INDEX metrics_envios_external_id_ix  ON metrics.envios (external_id);
CREATE INDEX metrics_envios_data_criacao_ix ON metrics.envios (data_criacao);

CREATE TABLE metrics.envios_diario (
  dia               DATE   NOT NULL,
  tipo_envio        TEXT   NOT NULL,
  tipo_comunicacao  TEXT   NOT NULL,
  criados           BIGINT NOT NULL DEFAULT 0,
  enviados          BIGINT NOT NULL DEFAULT 0,
  entregues         BIGINT NOT NULL DEFAULT 0,
  aberturas         BIGINT NOT NULL DEFAULT 0,
  cliques           BIGINT NOT NULL DEFAULT 0,
  bounces           BIGINT NOT NULL DEFAULT 0,
  reclamacoes       BIGINT NOT NULL DEFAULT 0,
  falhas            BIGINT NOT NULL DEFAULT 0,
  atualizado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT metrics_envios_diario_pk PRIMARY KEY (dia, tipo_envio, tipo_comunicacao)
);

CREATE TABLE metrics.solicitacoes_diario (
  dia                DATE   NOT NULL,
  tipo_servico_id    UUID   NOT NULL,
  tipo_servico_nome  TEXT   NOT NULL,
  concluidas         BIGINT NOT NULL DEFAULT 0,
  em_andamento       BIGINT NOT NULL DEFAULT 0,
  atualizado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT metrics_solicitacoes_diario_pk PRIMARY KEY (dia, tipo_servico_id)
);


-- =====================================================================
-- RLS — tenant por transação: SET LOCAL app.current_tenants = '<csv de uuids>'
-- Vazio/não setado = perfil global (admin/suporte): vê tudo, autorização por role.
-- =====================================================================

CREATE OR REPLACE FUNCTION public.current_tenant_ids() RETURNS uuid[]
LANGUAGE sql STABLE AS $$
  SELECT CASE
    WHEN COALESCE(current_setting('app.current_tenants', true), '') = '' THEN NULL
    ELSE string_to_array(current_setting('app.current_tenants', true), ',')::uuid[]
  END
$$;

-- Tabelas com imobiliaria_id próprio
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['imoveis','solicitacoes','contratos']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format($p$
      CREATE POLICY tenant_isolation ON %I
      USING (
        public.current_tenant_ids() IS NULL
        OR imobiliaria_id = ANY (public.current_tenant_ids())
      )$p$, t);
  END LOOP;
END $$;

-- clientes: mesma regra de isolamento por tenant (cada cliente é de uma imobiliária).
ALTER TABLE clientes ENABLE ROW LEVEL SECURITY;
ALTER TABLE clientes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON clientes
  USING (
    public.current_tenant_ids() IS NULL
    OR imobiliaria_id = ANY (public.current_tenant_ids())
  );

-- Tabelas com tenant indireto (EXISTS-subquery até o ancestral que carrega imobiliaria_id)
ALTER TABLE solicitacao_servicos ENABLE ROW LEVEL SECURITY;
ALTER TABLE solicitacao_servicos FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON solicitacao_servicos
  USING (
    public.current_tenant_ids() IS NULL
    OR EXISTS (SELECT 1 FROM solicitacoes s
               WHERE s.id = solicitacao_servicos.solicitacao_id
                 AND s.imobiliaria_id = ANY (public.current_tenant_ids()))
  );

ALTER TABLE solicitacao_intervenientes ENABLE ROW LEVEL SECURITY;
ALTER TABLE solicitacao_intervenientes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON solicitacao_intervenientes
  USING (
    public.current_tenant_ids() IS NULL
    OR EXISTS (SELECT 1 FROM solicitacoes s
               WHERE s.id = solicitacao_intervenientes.solicitacao_id
                 AND s.imobiliaria_id = ANY (public.current_tenant_ids()))
  );

ALTER TABLE laudos ENABLE ROW LEVEL SECURITY;
ALTER TABLE laudos FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON laudos
  USING (
    public.current_tenant_ids() IS NULL
    OR EXISTS (SELECT 1 FROM solicitacao_servicos ss
               JOIN solicitacoes s ON s.id = ss.solicitacao_id
               WHERE ss.id = laudos.solicitacao_servico_id
                 AND s.imobiliaria_id = ANY (public.current_tenant_ids()))
  );

ALTER TABLE atribuicoes ENABLE ROW LEVEL SECURITY;
ALTER TABLE atribuicoes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON atribuicoes
  USING (
    public.current_tenant_ids() IS NULL
    OR EXISTS (SELECT 1 FROM solicitacao_servicos ss
               JOIN solicitacoes s ON s.id = ss.solicitacao_id
               WHERE ss.id = atribuicoes.servico_id
                 AND s.imobiliaria_id = ANY (public.current_tenant_ids()))
  );

ALTER TABLE contestacoes ENABLE ROW LEVEL SECURITY;
ALTER TABLE contestacoes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contestacoes
  USING (
    public.current_tenant_ids() IS NULL
    OR EXISTS (SELECT 1 FROM laudos l
               JOIN solicitacao_servicos ss ON ss.id = l.solicitacao_servico_id
               JOIN solicitacoes s ON s.id = ss.solicitacao_id
               WHERE l.id = contestacoes.laudo_id
                 AND s.imobiliaria_id = ANY (public.current_tenant_ids()))
  );

ALTER TABLE reparos ENABLE ROW LEVEL SECURITY;
ALTER TABLE reparos FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON reparos
  USING (
    public.current_tenant_ids() IS NULL
    OR EXISTS (SELECT 1 FROM solicitacoes s
               WHERE s.id = reparos.solicitacao_id
                 AND s.imobiliaria_id = ANY (public.current_tenant_ids()))
    OR EXISTS (SELECT 1 FROM contestacoes c
               JOIN laudos l ON l.id = c.laudo_id
               JOIN solicitacao_servicos ss ON ss.id = l.solicitacao_servico_id
               JOIN solicitacoes s ON s.id = ss.solicitacao_id
               WHERE c.id = reparos.contestacao_id
                 AND s.imobiliaria_id = ANY (public.current_tenant_ids()))
  );

ALTER TABLE pagamentos ENABLE ROW LEVEL SECURITY;
ALTER TABLE pagamentos FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pagamentos
  USING (
    public.current_tenant_ids() IS NULL
    OR EXISTS (SELECT 1 FROM solicitacoes s
               WHERE s.id = pagamentos.solicitacao_id
                 AND s.imobiliaria_id = ANY (public.current_tenant_ids()))
  );

ALTER TABLE contrato_faixas ENABLE ROW LEVEL SECURITY;
ALTER TABLE contrato_faixas FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contrato_faixas
  USING (
    public.current_tenant_ids() IS NULL
    OR EXISTS (SELECT 1 FROM contratos c
               WHERE c.id = contrato_faixas.contrato_id
                 AND c.imobiliaria_id = ANY (public.current_tenant_ids()))
  );

ALTER TABLE contrato_repasses ENABLE ROW LEVEL SECURITY;
ALTER TABLE contrato_repasses FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contrato_repasses
  USING (
    public.current_tenant_ids() IS NULL
    OR EXISTS (SELECT 1 FROM contrato_faixas cf
               JOIN contratos c ON c.id = cf.contrato_id
               WHERE cf.id = contrato_repasses.contrato_faixa_id
                 AND c.imobiliaria_id = ANY (public.current_tenant_ids()))
  );

ALTER TABLE contrato_servico_grupos ENABLE ROW LEVEL SECURITY;
ALTER TABLE contrato_servico_grupos FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contrato_servico_grupos
  USING (
    public.current_tenant_ids() IS NULL
    OR EXISTS (SELECT 1 FROM contratos c
               WHERE c.id = contrato_servico_grupos.contrato_id
                 AND c.imobiliaria_id = ANY (public.current_tenant_ids()))
  );

-- signatarios: só documento_tipo='laudo' hoje; outros valores lançam antes de chegar aqui.
ALTER TABLE signatarios ENABLE ROW LEVEL SECURITY;
ALTER TABLE signatarios FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON signatarios
  USING (
    public.current_tenant_ids() IS NULL
    OR (
      signatarios.documento_tipo = 'laudo'
      AND EXISTS (SELECT 1 FROM laudos l
                  JOIN solicitacao_servicos ss ON ss.id = l.solicitacao_servico_id
                  JOIN solicitacoes s ON s.id = ss.solicitacao_id
                  WHERE l.id = signatarios.documento_id
                    AND s.imobiliaria_id = ANY (public.current_tenant_ids()))
    )
  );

-- pdf_templates: imobiliaria_id NULL = template global, visível a qualquer tenant.
ALTER TABLE pdf_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE pdf_templates FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pdf_templates
  USING (
    public.current_tenant_ids() IS NULL
    OR imobiliaria_id IS NULL
    OR imobiliaria_id = ANY (public.current_tenant_ids())
  );

-- ambientes -> imoveis.imobiliaria_id (1 hop); fotos -> ambientes -> imoveis (2 hops)
ALTER TABLE ambientes ENABLE ROW LEVEL SECURITY;
ALTER TABLE ambientes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ambientes
  USING (
    public.current_tenant_ids() IS NULL
    OR EXISTS (SELECT 1 FROM imoveis i
               WHERE i.id = ambientes.imovel_id
                 AND i.imobiliaria_id = ANY (public.current_tenant_ids()))
  );

ALTER TABLE fotos ENABLE ROW LEVEL SECURITY;
ALTER TABLE fotos FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON fotos
  USING (
    public.current_tenant_ids() IS NULL
    OR EXISTS (SELECT 1 FROM ambientes a
               JOIN imoveis i ON i.id = a.imovel_id
               WHERE a.id = fotos.ambiente_id
                 AND i.imobiliaria_id = ANY (public.current_tenant_ids()))
  );

-- servicos / servico_tipos / faixa_referencia / tipos_servico / interveniente_papeis:
-- catálogo global, sem RLS; leitura liberada, escrita gated por RBAC no inspecta-api (ou só banco).


-- =====================================================================
-- SEEDS (idempotentes)
-- =====================================================================

-- 5 faixas de metragem de referência (globais, estrutura fixa).
INSERT INTO faixa_referencia (id, ordem, metragem_min, metragem_max, rotulo) VALUES
  (1, 1,   0.00,  40.00, '0 – 40 m²'),
  (2, 2,  41.00,  70.00, '41 – 70 m²'),
  (3, 3,  71.00, 120.00, '71 – 120 m²'),
  (4, 4, 121.00, 195.00, '121 – 195 m²'),
  (5, 5, 195.01,   NULL, 'acima de 195 m² (+75 m²)')
ON CONFLICT (id) DO NOTHING;

-- Papéis de interveniente. Sem 'testemunha' (não é obrigatória para a Pleno).
INSERT INTO interveniente_papeis (slug, nome) VALUES
  ('locador',   'Locador'),
  ('locatario', 'Locatário'),
  ('fiador',    'Fiador')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO usuarios (id, imobiliaria_id, nome, email, senha_hash, perfil, status) VALUES
  ('00000000-0000-4000-8000-0000000000ad', NULL, 'Admin Inspecta', 'xena.w@example.org',
   '$2y$10$V8inrCWPVWcWI5zw2lBiV.Gmn8DWPIcxDvU/RUv3JZIY1tYFlbiKa', 'admin', 'ativo')
ON CONFLICT (email) DO NOTHING;

-- Vínculo editável utilidade -> template de e-mail (os 7 do sistema). template_id
-- aqui é o UUID padrão de cada utilidade (NotifyTemplates); só aponta para um
-- template real quando o ADMIN cria e vincula na tela de Utilidades.
INSERT INTO notify.template_utilidades (utilidade, template_id) VALUES
  ('password_reset',           '00000000-0000-4000-8000-000000000002'),
  ('user_invited',             '00000000-0000-4000-8000-000000000001'),
  ('api_key_expiring',         '00000000-0000-4000-8000-000000000003'),
  ('laudo_assinado',           '00000000-0000-4000-8000-000000000004'),
  ('pagamento_confirmado',     '00000000-0000-4000-8000-000000000005'),
  ('pagamento_link',           '00000000-0000-4000-8000-000000000006'),
  ('vistoriador_pleno_criado', '00000000-0000-4000-8000-000000000007'),
  ('assinatura_link',          '00000000-0000-4000-8000-000000000008'),
  ('assinatura_lembrete',      '00000000-0000-4000-8000-000000000009')
ON CONFLICT (utilidade) DO NOTHING;

-- =====================================================================
-- MERGED V2 — Fluxo de solicitação com pagamento (AbacatePay: produto + checkout).
-- The original V2__payment_flow.sql has been inlined here so the baseline
-- contains the payment-related schema changes in a single file.
--
-- Atribuição: deferred until payment confirmation. New columns and indexes:
--   * solicitacao_servicos.product_id (UUID) and solicitacao_servicos.external_product_id (VARCHAR)
--   * solicitacoes.checkout_id, solicitacoes.checkout_url, solicitacoes.payment_status
--   * imobiliarias.external_payment_id
-- =====================================================================
-- Indexes for payment-related columns (columns added inline in CREATE TABLE above).
CREATE INDEX IF NOT EXISTS idx_solicitacao_servicos_external_product ON solicitacao_servicos (external_product_id)
    WHERE external_product_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_solicitacoes_checkout_id ON solicitacoes (checkout_id)
    WHERE checkout_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_imobiliarias_external_payment_id ON imobiliarias (external_payment_id)
    WHERE external_payment_id IS NOT NULL;
