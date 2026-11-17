CREATE TABLE auditoria (
    id BIGSERIAL PRIMARY KEY,
    responsavel_id BIGINT NOT NULL,
    acao VARCHAR(50) NOT NULL,
    entidade VARCHAR(30) NOT NULL,
    entidade_id BIGINT,
    justificativa TEXT,
    detalhes TEXT,
    data_acao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_auditoria_responsavel
        FOREIGN KEY (responsavel_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_auditoria_data ON auditoria(data_acao DESC);
CREATE INDEX idx_auditoria_acao ON auditoria(acao);
CREATE INDEX idx_auditoria_responsavel ON auditoria(responsavel_id);

-- Auditoria não pode ser alterada nem apagada
CREATE FUNCTION bloquear_alteracao_auditoria() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Registros de auditoria não podem ser alterados nem apagados.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auditoria_imutavel
    BEFORE UPDATE OR DELETE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION bloquear_alteracao_auditoria();