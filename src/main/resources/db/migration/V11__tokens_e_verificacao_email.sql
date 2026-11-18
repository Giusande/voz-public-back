-- Contas existentes ficam como verificadas; só as novas nascem pendentes (decisão do código)
ALTER TABLE usuarios ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT TRUE;
-- Sobe a cada troca de senha: sessões abertas com versão antiga são encerradas
ALTER TABLE usuarios ADD COLUMN versao_credencial INTEGER NOT NULL DEFAULT 0;

CREATE TABLE tokens_conta (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_em TIMESTAMP NOT NULL,
    usado_em TIMESTAMP,

    CONSTRAINT fk_token_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT chk_token_tipo CHECK (tipo IN ('VERIFICACAO_EMAIL', 'REDEFINICAO_SENHA')),
    CONSTRAINT uk_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_tokens_pendentes ON tokens_conta(usuario_id, tipo) WHERE usado_em IS NULL;