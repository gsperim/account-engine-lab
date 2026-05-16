-- Índice parcial para o job de retenção: filtra apenas registros publicados com data de publicação definida.
-- Mantém o DELETE eficiente mesmo com tabela grande (evita full scan).
CREATE INDEX idx_outbox_publicado_em ON outbox (publicado_em ASC) WHERE publicado = TRUE;
