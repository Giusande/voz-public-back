CREATE TABLE unidades (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    pai_id BIGINT,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_unidade_pai FOREIGN KEY (pai_id) REFERENCES unidades(id),
    CONSTRAINT chk_unidade_tipo
        CHECK (tipo IN ('SECRETARIA', 'ORGAO', 'DEPARTAMENTO', 'EQUIPE')),
    CONSTRAINT chk_unidade_pai
        CHECK ((tipo IN ('SECRETARIA', 'ORGAO') AND pai_id IS NULL)
            OR (tipo IN ('DEPARTAMENTO', 'EQUIPE') AND pai_id IS NOT NULL))
);

-- Mesmo nome não se repete dentro da mesma unidade superior (ignora maiúsculas)
CREATE UNIQUE INDEX ux_unidades_pai_nome ON unidades (COALESCE(pai_id, 0), LOWER(nome));
CREATE INDEX idx_unidades_pai ON unidades(pai_id);

CREATE TABLE encaminhamentos (
    id BIGSERIAL PRIMARY KEY,
    denuncia_id BIGINT NOT NULL,
    unidade_id BIGINT NOT NULL,
    servidor_id BIGINT,
    prazo_especifico DATE,
    observacao TEXT,
    responsavel_id BIGINT NOT NULL,
    data_encaminhamento TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_encaminhamento_denuncia
        FOREIGN KEY (denuncia_id) REFERENCES denuncias(id) ON DELETE CASCADE,
    CONSTRAINT fk_encaminhamento_unidade
        FOREIGN KEY (unidade_id) REFERENCES unidades(id),
    CONSTRAINT fk_encaminhamento_servidor
        FOREIGN KEY (servidor_id) REFERENCES usuarios(id),
    CONSTRAINT fk_encaminhamento_responsavel
        FOREIGN KEY (responsavel_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_encaminhamentos_denuncia ON encaminhamentos(denuncia_id, id DESC);

-- Reaproveita a função criada na V4: encaminhamentos gravados não são editados
CREATE TRIGGER trg_encaminhamentos_imutavel
    BEFORE UPDATE ON encaminhamentos
    FOR EACH ROW EXECUTE FUNCTION bloquear_alteracao_historico();