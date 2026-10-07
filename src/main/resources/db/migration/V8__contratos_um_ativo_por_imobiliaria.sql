-- Biletagem no cadastro da imobiliária: no máximo um contrato ativo por tenant.
-- Dedup antes do índice (POST legado /v1/contratos podia deixar mais de um ativo).
UPDATE contratos AS c
SET status = 'inativo'
WHERE c.status = 'ativo'
  AND c.id NOT IN (
    SELECT kept.id FROM (
      SELECT DISTINCT ON (imobiliaria_id) id
      FROM contratos
      WHERE status = 'ativo'
      ORDER BY imobiliaria_id, criado_em DESC
    ) AS kept
  );

CREATE UNIQUE INDEX ux_contratos_imobiliaria_ativo
    ON contratos (imobiliaria_id)
    WHERE status = 'ativo';

-- Cadastro agora grava contrato na mesma transação; DELETE da imobiliária
-- precisa limpar o contrato (grupos/faixas já caem por ON DELETE CASCADE).
ALTER TABLE contratos
    DROP CONSTRAINT contratos_imobiliaria_id_fkey,
    ADD CONSTRAINT contratos_imobiliaria_id_fkey
        FOREIGN KEY (imobiliaria_id) REFERENCES imobiliarias(id) ON DELETE CASCADE;
