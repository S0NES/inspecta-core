-- Características do imóvel no cadastro (wizard step 2).
-- quartos: nulo quando o chip "Quarto" não está selecionado; senão >= 1.
-- cobertura: se o imóvel possui cobertura (chip liga/desliga).

ALTER TABLE imoveis
    ADD COLUMN quartos INTEGER,
    ADD COLUMN cobertura BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE imoveis
    ADD CONSTRAINT imoveis_quartos_ck CHECK (quartos IS NULL OR quartos >= 1);
