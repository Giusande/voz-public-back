CREATE TABLE evidencias (
    id BIGSERIAL PRIMARY KEY,
    historico_id BIGINT NOT NULL,
    caminho_arquivo VARCHAR(500) NOT NULL,
    data_upload TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_evidencia_historico
        FOREIGN KEY (historico_id) REFERENCES historico_status(id) ON DELETE CASCADE
);
CREATE INDEX idx_evidencias_historico ON evidencias(historico_id);

CREATE TABLE historico_prioridade (
    id BIGSERIAL PRIMARY KEY,
    denuncia_id BIGINT NOT NULL,
    prioridade_anterior VARCHAR(20),
    prioridade_nova VARCHAR(20) NOT NULL,
    responsavel_id BIGINT NOT NULL,
    observacao TEXT,
    data_alteracao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_hist_prioridade_denuncia
        FOREIGN KEY (denuncia_id) REFERENCES denuncias(id) ON DELETE CASCADE,
    CONSTRAINT fk_hist_prioridade_responsavel
        FOREIGN KEY (responsavel_id) REFERENCES usuarios(id),
    CONSTRAINT chk_hist_prioridade_anterior
        CHECK (prioridade_anterior IS NULL
               OR prioridade_anterior IN ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE')),
    CONSTRAINT chk_hist_prioridade_nova
        CHECK (prioridade_nova IN ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE'))
);
CREATE INDEX idx_hist_prioridade_denuncia ON historico_prioridade(denuncia_id);

CREATE FUNCTION bloquear_alteracao_historico() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Registros de histórico não podem ser alterados.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_historico_status_imutavel
    BEFORE UPDATE ON historico_status
    FOR EACH ROW EXECUTE FUNCTION bloquear_alteracao_historico();

CREATE TRIGGER trg_historico_prioridade_imutavel
    BEFORE UPDATE ON historico_prioridade
    FOR EACH ROW EXECUTE FUNCTION bloquear_alteracao_historico();