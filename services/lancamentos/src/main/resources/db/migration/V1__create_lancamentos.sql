CREATE TABLE lancamentos (
    id               UUID            NOT NULL,
    tipo             VARCHAR(10)     NOT NULL,
    valor            DECIMAL(19, 2)  NOT NULL,
    descricao        VARCHAR(255),
    data_competencia DATE            NOT NULL,
    operador_id      VARCHAR(255)    NOT NULL,
    criado_em        TIMESTAMP       NOT NULL,
    CONSTRAINT pk_lancamentos PRIMARY KEY (id)
);

CREATE INDEX idx_lancamentos_data_competencia      ON lancamentos (data_competencia);
CREATE INDEX idx_lancamentos_data_competencia_tipo ON lancamentos (data_competencia, tipo);
