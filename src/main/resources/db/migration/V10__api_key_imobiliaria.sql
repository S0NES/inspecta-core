-- API keys passam a ser vinculadas a uma IMOBILIÁRIA (não mais a um usuário).
-- `ultimos4` guarda só os 4 últimos caracteres do secret para a lista mascarada
-- (`••••1a2b`); o plaintext continua sem ser persistido (só o hash).
--
-- usuario_id fica (nullable) para as chaves legadas continuarem autenticando.

ALTER TABLE tokens
    ADD COLUMN imobiliaria_id UUID REFERENCES imobiliarias(id) ON DELETE CASCADE,
    ADD COLUMN ultimos4       VARCHAR(4);

CREATE INDEX idx_tokens_imobiliaria ON tokens (imobiliaria_id)
    WHERE tipo = 'api_key' AND NOT revogado;
