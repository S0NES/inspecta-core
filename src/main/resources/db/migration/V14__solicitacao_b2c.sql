-- Solicitação B2C (Página 4 do prompt "4 telas B2C"): rascunho com hold de
-- vistoriador antes do pagamento.
--
-- tipos_servico com id fixo: o catálogo B2B é livre (admin cadastra qualquer
-- nome), mas o B2C precisa de um tipo_servico_id estável por client_service
-- (enum hardcoded no front) para que solicitacao_servicos.tipo_servico_id
-- continue NOT NULL sem depender de lookup em runtime. ClientServiceCatalog
-- (Java) referencia estes UUIDs diretamente.
INSERT INTO tipos_servico (id, nome, duracao_config, ativo) VALUES
    ('00000000-b2c0-0000-0000-000000000001', 'Vistoria de Entrada', '{}', TRUE),
    ('00000000-b2c0-0000-0000-000000000002', 'Vistoria de Saída', '{}', TRUE);

-- client_service: qual dos dois tipos acima esta linha representa, para a
-- biletagem B2C (PrecoService.resolverB2c) e o nome do produto no checkout.
-- NULL em toda linha B2B (não muda nada do fluxo existente).
ALTER TABLE solicitacao_servicos
    ADD COLUMN client_service TEXT CHECK (client_service IN ('vistoria_entrada', 'vistoria_saida'));

-- Vínculo solicitação -> cliente B2C. Distinto de solicitacoes.cliente_id, que
-- referencia a tabela clientes B2B (tenant-scoped) — nunca os dois preenchidos
-- na mesma linha.
ALTER TABLE solicitacoes
    ADD COLUMN cliente_b2c_id UUID REFERENCES clientes_b2c(id);
