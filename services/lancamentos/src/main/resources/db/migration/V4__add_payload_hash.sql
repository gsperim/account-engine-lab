-- Fingerprint do payload para detecção de conflito de idempotência.
-- Linhas legacy (criadas antes desta migration) recebem o próprio id como hash —
-- tentativas de replay de lançamentos antigos serão tratadas como conflito,
-- comportamento correto pois o payload original não está disponível para comparação.
ALTER TABLE lancamentos ADD COLUMN payload_hash VARCHAR(64);
UPDATE lancamentos SET payload_hash = encode(decode(replace(id::text, '-', ''), 'hex'), 'hex') WHERE payload_hash IS NULL;
ALTER TABLE lancamentos ALTER COLUMN payload_hash SET NOT NULL;
