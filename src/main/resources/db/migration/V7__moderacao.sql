ALTER TABLE denuncias ADD COLUMN oculta BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE comentarios ADD COLUMN oculto BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE sinalizacoes (
    id BIGSERIAL PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,
    alvo_id BIGINT NOT NULL,
    denuncia_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    motivo VARCHAR(30) NOT NULL,
    detalhe VARCHAR(500) NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    analisado_por BIGINT,
    data_analise TIMESTAMP,
    justificativa_analise TEXT,

    CONSTRAINT fk_sinalizacao_denuncia
        FOREIGN KEY (denuncia_id) REFERENCES denuncias(id) ON DELETE CASCADE,
    CONSTRAINT fk_sinalizacao_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_sinalizacao_analista
        FOREIGN KEY (analisado_por) REFERENCES usuarios(id),
    CONSTRAINT chk_sinalizacao_tipo CHECK (tipo IN ('DENUNCIA', 'COMENTARIO')),
    CONSTRAINT chk_sinalizacao_motivo
        CHECK (motivo IN ('SPAM', 'OFENSIVO', 'FALSO', 'DADOS_PESSOAIS', 'OUTRO')),
    CONSTRAINT chk_sinalizacao_status
        CHECK (status IN ('ABERTA', 'PROCEDENTE', 'IMPROCEDENTE')),
    CONSTRAINT chk_sinalizacao_analise
        CHECK ((status = 'ABERTA' AND analisado_por IS NULL)
            OR (status <> 'ABERTA' AND analisado_por IS NOT NULL)),
    CONSTRAINT uk_sinalizacao_usuario_alvo UNIQUE (usuario_id, tipo, alvo_id)
);

CREATE INDEX idx_sinalizacoes_status ON sinalizacoes(status, id);
CREATE INDEX idx_sinalizacoes_alvo ON sinalizacoes(tipo, alvo_id);