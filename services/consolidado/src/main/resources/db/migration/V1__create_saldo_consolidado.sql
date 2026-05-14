CREATE TABLE saldo_consolidado (
    data               DATE            NOT NULL,
    total_creditos     DECIMAL(19, 2)  NOT NULL DEFAULT 0,
    total_debitos      DECIMAL(19, 2)  NOT NULL DEFAULT 0,
    total_lancamentos  INT             NOT NULL DEFAULT 0,
    ultima_atualizacao TIMESTAMP       NOT NULL,
    CONSTRAINT pk_saldo_consolidado PRIMARY KEY (data)
);
