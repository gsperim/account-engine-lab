CREATE TABLE outbox (
    id            UUID        NOT NULL,
    lancamento_id UUID        NOT NULL,
    evento_tipo   VARCHAR(50) NOT NULL,
    payload       TEXT        NOT NULL,
    criado_em     TIMESTAMP   NOT NULL DEFAULT NOW(),
    publicado     BOOLEAN     NOT NULL DEFAULT FALSE,
    publicado_em  TIMESTAMP,
    tentativas    INT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_outbox PRIMARY KEY (id),
    CONSTRAINT fk_outbox_lancamento FOREIGN KEY (lancamento_id) REFERENCES lancamentos(id)
);

-- índice parcial: só entradas não publicadas, mantém scan rápido mesmo com tabela grande
CREATE INDEX idx_outbox_pendente ON outbox (criado_em ASC) WHERE publicado = FALSE;
