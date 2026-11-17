CREATE TABLE notificacoes (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    mensagem VARCHAR(600) NOT NULL,
    denuncia_id BIGINT,
    lida BOOLEAN NOT NULL DEFAULT FALSE,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notificacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_notificacao_denuncia FOREIGN KEY (denuncia_id) REFERENCES denuncias(id) ON DELETE CASCADE
);
CREATE INDEX idx_notificacoes_usuario ON notificacoes(usuario_id, lida, id DESC);

CREATE TABLE solicitacoes_info (
    id BIGSERIAL PRIMARY KEY,
    denuncia_id BIGINT NOT NULL,
    solicitante_id BIGINT NOT NULL,
    mensagem VARCHAR(1000) NOT NULL,
    data_solicitacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resposta VARCHAR(2000),
    data_resposta TIMESTAMP,

    CONSTRAINT fk_solicitacao_denuncia FOREIGN KEY (denuncia_id) REFERENCES denuncias(id) ON DELETE CASCADE,
    CONSTRAINT fk_solicitacao_solicitante FOREIGN KEY (solicitante_id) REFERENCES usuarios(id),
    CONSTRAINT chk_solicitacao_resposta CHECK ((resposta IS NULL) = (data_resposta IS NULL))
);
CREATE INDEX idx_solicitacoes_denuncia ON solicitacoes_info(denuncia_id, id DESC);
-- no máximo um pedido aberto (sem resposta) por ocorrência
CREATE UNIQUE INDEX ux_solicitacao_aberta ON solicitacoes_info(denuncia_id) WHERE resposta IS NULL;

-- Só é permitido preencher a resposta, uma única vez
CREATE FUNCTION proteger_solicitacao() RETURNS trigger AS $$
BEGIN
    IF OLD.resposta IS NOT NULL
       OR NEW.mensagem <> OLD.mensagem
       OR NEW.denuncia_id <> OLD.denuncia_id
       OR NEW.solicitante_id <> OLD.solicitante_id
       OR NEW.data_solicitacao <> OLD.data_solicitacao THEN
        RAISE EXCEPTION 'Solicitações só podem ser respondidas uma vez e não podem ser editadas.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_solicitacao_protegida
    BEFORE UPDATE ON solicitacoes_info
    FOR EACH ROW EXECUTE FUNCTION proteger_solicitacao();

CREATE TABLE confirmacoes (
    id BIGSERIAL PRIMARY KEY,
    denuncia_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    data_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_confirmacao_denuncia FOREIGN KEY (denuncia_id) REFERENCES denuncias(id) ON DELETE CASCADE,
    CONSTRAINT fk_confirmacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT chk_confirmacao_tipo
        CHECK (tipo IN ('TAMBEM_EXISTE', 'CONTINUA_PRESENTE', 'APARENTEMENTE_RESOLVIDO')),
    CONSTRAINT uk_confirmacao UNIQUE (denuncia_id, usuario_id)
);