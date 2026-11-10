CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL,
    telefone VARCHAR(20),
    papel VARCHAR(20) NOT NULL DEFAULT 'USUARIO',
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_cadastro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_usuario_papel
        CHECK (papel IN ('USUARIO', 'ADMIN'))
);

CREATE TABLE denuncias (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    descricao TEXT NOT NULL,
    endereco VARCHAR(255),
    bairro VARCHAR(100) NOT NULL,
    regiao VARCHAR(50) NOT NULL,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    categoria VARCHAR(60) NOT NULL,
    prioridade VARCHAR(20) NOT NULL DEFAULT 'MEDIA',
    status VARCHAR(30) NOT NULL DEFAULT 'ABERTA',
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_denuncia_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuarios(id),

    CONSTRAINT chk_denuncia_prioridade
        CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE')),

    CONSTRAINT chk_denuncia_status
        CHECK (status IN ('ABERTA', 'EM_ANALISE', 'EM_ANDAMENTO', 'RESOLVIDA', 'ARQUIVADA'))
);

CREATE TABLE fotos (
    id BIGSERIAL PRIMARY KEY,
    denuncia_id BIGINT NOT NULL,
    caminho_arquivo VARCHAR(500) NOT NULL,
    data_upload TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_foto_denuncia
        FOREIGN KEY (denuncia_id)
        REFERENCES denuncias(id)
        ON DELETE CASCADE
);

CREATE TABLE comentarios (
    id BIGSERIAL PRIMARY KEY,
    denuncia_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    conteudo TEXT NOT NULL,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_comentario_denuncia
        FOREIGN KEY (denuncia_id)
        REFERENCES denuncias(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_comentario_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuarios(id)
);

CREATE TABLE curtidas (
    id BIGSERIAL PRIMARY KEY,
    denuncia_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_curtida_denuncia
        FOREIGN KEY (denuncia_id)
        REFERENCES denuncias(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_curtida_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuarios(id),

    CONSTRAINT uk_curtida_usuario_denuncia
        UNIQUE (denuncia_id, usuario_id)
);

CREATE TABLE incentivos (
    id BIGSERIAL PRIMARY KEY,
    denuncia_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_incentivo_denuncia
        FOREIGN KEY (denuncia_id)
        REFERENCES denuncias(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_incentivo_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuarios(id),

    CONSTRAINT uk_incentivo_usuario_denuncia
        UNIQUE (denuncia_id, usuario_id)
);

CREATE INDEX idx_denuncias_usuario
    ON denuncias(usuario_id);

CREATE INDEX idx_denuncias_bairro
    ON denuncias(bairro);

CREATE INDEX idx_denuncias_regiao
    ON denuncias(regiao);

CREATE INDEX idx_denuncias_categoria
    ON denuncias(categoria);

CREATE INDEX idx_denuncias_prioridade
    ON denuncias(prioridade);

CREATE INDEX idx_denuncias_status
    ON denuncias(status);

CREATE INDEX idx_comentarios_denuncia
    ON comentarios(denuncia_id);

CREATE INDEX idx_fotos_denuncia
    ON fotos(denuncia_id);
