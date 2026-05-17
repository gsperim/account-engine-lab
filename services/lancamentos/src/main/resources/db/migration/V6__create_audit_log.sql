CREATE TABLE audit_log (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    operador_id VARCHAR(255) NOT NULL,
    acao        VARCHAR(100) NOT NULL,
    recurso_id  UUID,
    contexto    TEXT,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_operador ON audit_log(operador_id, criado_em DESC);
CREATE INDEX idx_audit_log_recurso  ON audit_log(recurso_id) WHERE recurso_id IS NOT NULL;
CREATE INDEX idx_audit_log_acao     ON audit_log(acao, criado_em DESC);
