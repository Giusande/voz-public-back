ALTER TABLE usuarios DROP CONSTRAINT chk_usuario_papel;

UPDATE usuarios SET papel = 'CIDADAO'       WHERE papel = 'USUARIO';
UPDATE usuarios SET papel = 'ADMINISTRADOR' WHERE papel = 'ADMIN';

ALTER TABLE usuarios ALTER COLUMN papel SET DEFAULT 'CIDADAO';

ALTER TABLE usuarios ADD CONSTRAINT chk_usuario_papel
    CHECK (papel IN ('CIDADAO', 'SERVIDOR', 'ADMINISTRADOR'));

CREATE UNIQUE INDEX ux_usuarios_email_lower ON usuarios (LOWER(email));