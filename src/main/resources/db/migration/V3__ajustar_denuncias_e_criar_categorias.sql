CREATE TABLE categorias (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(60) NOT NULL UNIQUE,
    prazo_referencia_dias INTEGER NOT NULL DEFAULT 30,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_categoria_prazo CHECK (prazo_referencia_dias > 0)
);

INSERT INTO categorias (nome, prazo_referencia_dias) VALUES
    ('Buraco na via', 30),
    ('Iluminação pública', 15),
    ('Lixo e entulho', 7),
    ('Alagamento e drenagem', 15),
    ('Calçada danificada', 45),
    ('Saneamento básico', 30),
    ('Sinalização de trânsito', 20),
    ('Praça e espaço público', 45),
    ('Árvore com risco', 10),
    ('Outros', 30);

DROP INDEX idx_denuncias_categoria;
ALTER TABLE denuncias DROP COLUMN categoria;
ALTER TABLE denuncias ADD COLUMN categoria_id BIGINT NOT NULL;
ALTER TABLE denuncias ADD CONSTRAINT fk_denuncia_categoria
    FOREIGN KEY (categoria_id) REFERENCES categorias(id);
CREATE INDEX idx_denuncias_categoria_id ON denuncias(categoria_id);

ALTER TABLE denuncias DROP CONSTRAINT chk_denuncia_prioridade;
ALTER TABLE denuncias RENAME COLUMN prioridade TO prioridade_sugerida;
ALTER TABLE denuncias ADD COLUMN prioridade_oficial VARCHAR(20);

ALTER TABLE denuncias ADD CONSTRAINT chk_denuncia_prioridade_sugerida
    CHECK (prioridade_sugerida IN ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE'));
ALTER TABLE denuncias ADD CONSTRAINT chk_denuncia_prioridade_oficial
    CHECK (prioridade_oficial IS NULL
           OR prioridade_oficial IN ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE'));

ALTER TABLE denuncias DROP CONSTRAINT chk_denuncia_status;
ALTER TABLE denuncias ALTER COLUMN status SET DEFAULT 'RECEBIDA';
ALTER TABLE denuncias ADD CONSTRAINT chk_denuncia_status
    CHECK (status IN ('RECEBIDA', 'EM_ANALISE', 'ENCAMINHADA', 'EM_EXECUCAO',
                      'RESOLVIDA', 'INDEFERIDA', 'REABERTA'));

CREATE TABLE historico_status (
    id BIGSERIAL PRIMARY KEY,
    denuncia_id BIGINT NOT NULL,
    status_anterior VARCHAR(30),
    status_novo VARCHAR(30) NOT NULL,
    responsavel_id BIGINT NOT NULL,
    observacao TEXT,
    data_alteracao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_historico_denuncia
        FOREIGN KEY (denuncia_id) REFERENCES denuncias(id) ON DELETE CASCADE,
    CONSTRAINT fk_historico_responsavel
        FOREIGN KEY (responsavel_id) REFERENCES usuarios(id),
    CONSTRAINT chk_historico_status_anterior
        CHECK (status_anterior IS NULL OR status_anterior IN
            ('RECEBIDA', 'EM_ANALISE', 'ENCAMINHADA', 'EM_EXECUCAO',
             'RESOLVIDA', 'INDEFERIDA', 'REABERTA')),
    CONSTRAINT chk_historico_status_novo
        CHECK (status_novo IN
            ('RECEBIDA', 'EM_ANALISE', 'ENCAMINHADA', 'EM_EXECUCAO',
             'RESOLVIDA', 'INDEFERIDA', 'REABERTA'))
);

CREATE INDEX idx_historico_denuncia ON historico_status(denuncia_id);