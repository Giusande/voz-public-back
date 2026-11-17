ALTER TABLE denuncias ADD COLUMN duplicada_de_id BIGINT;
ALTER TABLE denuncias ADD CONSTRAINT fk_denuncia_duplicada_de
    FOREIGN KEY (duplicada_de_id) REFERENCES denuncias(id);
ALTER TABLE denuncias ADD CONSTRAINT chk_denuncia_duplicada_propria
    CHECK (duplicada_de_id IS NULL OR duplicada_de_id <> id);
CREATE INDEX idx_denuncias_duplicada_de ON denuncias(duplicada_de_id)
    WHERE duplicada_de_id IS NOT NULL;

-- Sem cadeias: a principal não é filha, e quem tem filhas não vira filha
CREATE FUNCTION validar_agrupamento() RETURNS trigger AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM denuncias WHERE id = NEW.duplicada_de_id AND duplicada_de_id IS NOT NULL) THEN
        RAISE EXCEPTION 'A ocorrência principal não pode ser, ela mesma, uma duplicada.';
    END IF;
    IF EXISTS (SELECT 1 FROM denuncias WHERE duplicada_de_id = NEW.id) THEN
        RAISE EXCEPTION 'Uma ocorrência que já reúne duplicadas não pode ser agrupada a outra.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_denuncias_agrupamento
    BEFORE INSERT OR UPDATE ON denuncias
    FOR EACH ROW WHEN (NEW.duplicada_de_id IS NOT NULL)
    EXECUTE FUNCTION validar_agrupamento();

-- Histórico de agrupamentos (imutável)
CREATE TABLE agrupamentos (
    id BIGSERIAL PRIMARY KEY,
    denuncia_id BIGINT NOT NULL,
    principal_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    responsavel_id BIGINT NOT NULL,
    justificativa TEXT NOT NULL,
    data_acao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_agrupamento_denuncia
        FOREIGN KEY (denuncia_id) REFERENCES denuncias(id) ON DELETE CASCADE,
    CONSTRAINT fk_agrupamento_principal
        FOREIGN KEY (principal_id) REFERENCES denuncias(id),
    CONSTRAINT fk_agrupamento_responsavel
        FOREIGN KEY (responsavel_id) REFERENCES usuarios(id),
    CONSTRAINT chk_agrupamento_tipo CHECK (tipo IN ('AGRUPADA', 'DESAGRUPADA')),
    CONSTRAINT chk_agrupamento_distintas CHECK (denuncia_id <> principal_id)
);
CREATE INDEX idx_agrupamentos_denuncia ON agrupamentos(denuncia_id, id);
CREATE INDEX idx_agrupamentos_principal ON agrupamentos(principal_id);

-- Reaproveita a função criada na V4
CREATE TRIGGER trg_agrupamentos_imutavel
    BEFORE UPDATE ON agrupamentos
    FOR EACH ROW EXECUTE FUNCTION bloquear_alteracao_historico();