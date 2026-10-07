-- b2c_service: catálogo dos serviços que um Cliente B2C pode ser habilitado a
-- solicitar (ex.: "Vistoria de Entrada"). Substitui o enum hardcoded
-- client_service (vistoria_entrada/vistoria_saida, ver V12/V13/V14) por linhas
-- de tabela — mesma ideia, mas fácil de adicionar um novo serviço sem deploy
-- (B2cServicoService cria a linha aqui + o tipos_servico correspondente).
CREATE TABLE b2c_service (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome            TEXT NOT NULL,
    descricao       TEXT NOT NULL DEFAULT '',
    icone           TEXT NOT NULL DEFAULT '',
    tipo_servico_id UUID NOT NULL REFERENCES tipos_servico(id),
    ativo           BOOLEAN NOT NULL DEFAULT TRUE,
    ordem           SMALLINT NOT NULL DEFAULT 0,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed com os 2 serviços existentes, reaproveitando os tipos_servico fixos da
-- V14 — a atribuição/preço já configurados pra eles não mudam de lugar.
INSERT INTO b2c_service (id, nome, descricao, icone, tipo_servico_id, ordem) VALUES
    ('00000000-b2c2-0000-0000-000000000001', 'Vistoria de Entrada',
     'Documenta o imóvel antes de você receber as chaves.', 'key',
     '00000000-b2c0-0000-0000-000000000001', 1),
    ('00000000-b2c2-0000-0000-000000000002', 'Vistoria de Saída',
     'Registra o estado do imóvel na devolução das chaves.', 'log-out',
     '00000000-b2c0-0000-0000-000000000002', 2);

-- clientes_b2c.client_services: migra os valores do enum antigo pros ids acima.
UPDATE clientes_b2c
SET client_services = (
    SELECT COALESCE(jsonb_agg(
        CASE elem
            WHEN '"vistoria_entrada"' THEN '"00000000-b2c2-0000-0000-000000000001"'::jsonb
            WHEN '"vistoria_saida"'   THEN '"00000000-b2c2-0000-0000-000000000002"'::jsonb
            ELSE elem
        END
    ), '[]'::jsonb)
    FROM jsonb_array_elements(client_services) elem
);

-- biletagem_cliente_percentual: PK client_service (texto) -> b2c_service_id (UUID, FK).
ALTER TABLE biletagem_cliente_percentual ADD COLUMN b2c_service_id UUID;
UPDATE biletagem_cliente_percentual SET b2c_service_id = CASE client_service
    WHEN 'vistoria_entrada' THEN '00000000-b2c2-0000-0000-000000000001'::uuid
    WHEN 'vistoria_saida'   THEN '00000000-b2c2-0000-0000-000000000002'::uuid
END;
ALTER TABLE biletagem_cliente_percentual DROP COLUMN client_service CASCADE;
ALTER TABLE biletagem_cliente_percentual ALTER COLUMN b2c_service_id SET NOT NULL;
ALTER TABLE biletagem_cliente_percentual ADD PRIMARY KEY (b2c_service_id);
ALTER TABLE biletagem_cliente_percentual
    ADD CONSTRAINT biletagem_cliente_percentual_service_fk FOREIGN KEY (b2c_service_id) REFERENCES b2c_service(id);

-- biletagem_cliente_faixas: mesma troca; chave composta com faixa_referencia_id.
ALTER TABLE biletagem_cliente_faixas ADD COLUMN b2c_service_id UUID;
UPDATE biletagem_cliente_faixas SET b2c_service_id = CASE client_service
    WHEN 'vistoria_entrada' THEN '00000000-b2c2-0000-0000-000000000001'::uuid
    WHEN 'vistoria_saida'   THEN '00000000-b2c2-0000-0000-000000000002'::uuid
END;
ALTER TABLE biletagem_cliente_faixas DROP COLUMN client_service CASCADE;
ALTER TABLE biletagem_cliente_faixas ALTER COLUMN b2c_service_id SET NOT NULL;
ALTER TABLE biletagem_cliente_faixas
    ADD CONSTRAINT biletagem_cliente_faixas_service_fk FOREIGN KEY (b2c_service_id) REFERENCES b2c_service(id);
ALTER TABLE biletagem_cliente_faixas
    ADD CONSTRAINT biletagem_cliente_faixas_service_faixa_unique UNIQUE (b2c_service_id, faixa_referencia_id);

-- solicitacao_servicos.client_service (texto livre) -> b2c_service_id (UUID, FK).
ALTER TABLE solicitacao_servicos ADD COLUMN b2c_service_id UUID REFERENCES b2c_service(id);
UPDATE solicitacao_servicos SET b2c_service_id = CASE client_service
    WHEN 'vistoria_entrada' THEN '00000000-b2c2-0000-0000-000000000001'::uuid
    WHEN 'vistoria_saida'   THEN '00000000-b2c2-0000-0000-000000000002'::uuid
    ELSE NULL
END;
ALTER TABLE solicitacao_servicos DROP COLUMN client_service;
