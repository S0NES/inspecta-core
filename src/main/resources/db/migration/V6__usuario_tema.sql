-- =====================================================================
-- V6 — Tema (light/dark) persistido na conta do usuário.
-- A SPA lia só localStorage; ao logar em outro navegador o tema voltava
-- ao padrão. A coluna segue o usuário e o GET /v1/usuarios/me devolve.
-- =====================================================================

ALTER TABLE usuarios
    ADD COLUMN tema TEXT NOT NULL DEFAULT 'light'
        CHECK (tema IN ('light', 'dark'));
