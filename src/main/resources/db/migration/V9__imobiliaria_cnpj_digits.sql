-- CNPJ passa a ser guardado só com dígitos (14 chars), não mais formatado.
-- A UNIQUE de imobiliarias.cnpj só é significativa se a forma persistida for canônica:
-- antes "11.222.333/0001-81" e "11222333000181" eram considerados diferentes.

UPDATE imobiliarias SET cnpj = regexp_replace(cnpj, '\D', '', 'g');

ALTER TABLE imobiliarias ALTER COLUMN cnpj TYPE VARCHAR(14);
