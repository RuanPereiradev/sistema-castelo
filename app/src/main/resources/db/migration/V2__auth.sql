-- Pressupõe banco sem dados de produção. Duas situações falham alto de propósito,
-- derrubando a migration inteira em vez de corrigir dado em silêncio:
--   * dois e-mails com o mesmo prefixo (ignorando maiúsculas) geram o mesmo username
--     e violam o índice único uk_app_user_username;
--   * prefixo de e-mail com mais de 30 caracteres estoura a coluna username VARCHAR(30).

ALTER TABLE app_user
    ADD COLUMN username      VARCHAR(30),
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

-- email deixa de ser obrigatório: cozinheiro pode não ter
ALTER TABLE app_user ALTER COLUMN email DROP NOT NULL;

-- unicidade do email só quando presente
ALTER TABLE app_user DROP CONSTRAINT uk_app_user_email;
CREATE UNIQUE INDEX uk_app_user_email ON app_user (email) WHERE email IS NOT NULL;

-- username é o novo campo de login
UPDATE app_user SET username = split_part(email, '@', 1) WHERE username IS NULL;
ALTER TABLE app_user ALTER COLUMN username SET NOT NULL;
CREATE UNIQUE INDEX uk_app_user_username ON app_user (lower(username));
