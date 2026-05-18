-- Rastreamento de eventos já aplicados ao saldo consolidado.
-- Garante idempotência diante de redelivery at-least-once do RabbitMQ:
-- a inserção com PK única falha silenciosamente caso o evento chegue duas vezes.
CREATE TABLE lancamentos_aplicados (
    lancamento_id     UUID         NOT NULL,
    data_competencia  DATE         NOT NULL,
    aplicado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_lancamentos_aplicados PRIMARY KEY (lancamento_id)
);

CREATE INDEX idx_lancamentos_aplicados_data ON lancamentos_aplicados (data_competencia);
