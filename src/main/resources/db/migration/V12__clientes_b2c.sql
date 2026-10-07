-- Cliente B2C (Páginas 1-2 do prompt "4 telas B2C"). Distinto da `clientes` B2B
-- (tenant-scoped por imobiliaria_id, com RLS) — o cliente B2C não pertence a
-- nenhuma imobiliária, então não faz sentido reaproveitar aquela tabela/RLS.
--
-- client_service é o enum hardcoded da biletagem/solicitação B2C (não é o
-- catálogo tipos_servico/servicos usado no B2B).

CREATE TABLE clientes_b2c (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL UNIQUE REFERENCES usuarios(id),
    nome            TEXT NOT NULL,
    email           TEXT NOT NULL UNIQUE,
    cpf             TEXT NOT NULL,
    telefone        TEXT NOT NULL,
    cidade_id       UUID NOT NULL REFERENCES cidades(id),
    -- valores validados em ClienteB2cService (enum ClientService); é sempre o
    -- backend que monta este array, nunca texto livre do cliente.
    client_services JSONB NOT NULL DEFAULT '[]',
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Sem coluna ativo própria: o estado (convidado/ativo/inativo) já vive em
-- usuarios.status (1:1 via usuario_id) — inativar cliente = inativar o usuário,
-- mesma ação que já existe para o resto da equipe.

CREATE INDEX idx_clientes_b2c_cidade ON clientes_b2c (cidade_id);
