-- Biletagem Cliente (Página 3 do prompt "4 telas B2C"): preço B2C global,
-- um contrato só (não por imobiliária, não por produto do catálogo B2B).
-- Chaveado por client_service (vistoria_entrada/vistoria_saida), não por
-- servico_id/tipo_servico_id.

CREATE TABLE biletagem_cliente (
    id   BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
    modo TEXT NOT NULL CHECK (modo IN ('percentual', 'faixa'))
);

CREATE TABLE biletagem_cliente_percentual (
    client_service TEXT PRIMARY KEY CHECK (client_service IN ('vistoria_entrada', 'vistoria_saida')),
    percentual     NUMERIC(5,2) NOT NULL,
    minimo         NUMERIC(12,2)
);

-- Duas colunas (sem/com mobiliado) porque o Passo 1 do wizard de solicitação
-- B2C (Página 4) já coleta "mobiliado" — mesmo padrão de contrato_faixas.
CREATE TABLE biletagem_cliente_faixas (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_service      TEXT NOT NULL CHECK (client_service IN ('vistoria_entrada', 'vistoria_saida')),
    faixa_referencia_id SMALLINT NOT NULL REFERENCES faixa_referencia(id),
    valor_sem_mob       NUMERIC(12,2) NOT NULL,
    valor_com_mob       NUMERIC(12,2) NOT NULL,
    UNIQUE (client_service, faixa_referencia_id)
);
