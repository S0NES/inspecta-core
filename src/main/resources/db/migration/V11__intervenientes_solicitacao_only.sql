-- Interveniente deixa de existir no escopo do IMÓVEL. Passa a existir apenas no
-- escopo da SOLICITAÇÃO (solicitacao_intervenientes), que já é a fonte dos
-- signatários da assinatura. O espelho Pleno também lê daqui agora.
--
-- Novidades em solicitacao_intervenientes:
--   * acompanhante: quem acompanha a vistoria (0+; opcional). É sempre um dos
--     intervenientes da solicitação — nunca uma lista à parte.
--   * telefone: capturado no wizard (opcional).

ALTER TABLE solicitacao_intervenientes
    ADD COLUMN acompanhante BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN telefone     TEXT;

DROP TABLE IF EXISTS intervenientes;

-- interveniente_papeis (lookup de papéis) permanece — é usado pela lista da solicitação.
