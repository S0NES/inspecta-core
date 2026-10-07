-- =====================================================================
-- V2 — Endereço próprio da imobiliária (spec: imobiliaria.md).
--
-- A imobiliária é uma empresa parceira, não só um imóvel: precisa de
-- endereço próprio. Mesma convenção do imóvel (imoveis.endereco/numero/
-- complemento/cep + cidade_id FK para cidades) — cidades continua sendo a
-- fonte única de "quais praças a Inspecta atende".
--
-- Tudo NULLABLE no schema; a obrigatoriedade (endereco + cidade_id no
-- create/edit) fica na camada de UI/serviço, não no banco.
--
-- Espelhamento Pleno: SEM impacto. A pessoa que a Pleno recebe para a
-- imobiliária é fictícia (DadosFalsos) — o endereço real NÃO entra em
-- nenhum payload de espelhamento.
-- =====================================================================

ALTER TABLE imobiliarias
    ADD COLUMN cep         VARCHAR(9),
    ADD COLUMN endereco    VARCHAR(255),
    ADD COLUMN numero      VARCHAR(20),
    ADD COLUMN complemento VARCHAR(120),
    ADD COLUMN cidade_id   UUID REFERENCES cidades(id);
