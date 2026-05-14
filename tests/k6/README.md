# Testes de Carga — k6

Suíte de testes de performance mapeada nos NFRs do desafio.

## Pré-requisitos

```bash
# Ubuntu/Debian
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# Ou via Docker (sem instalar)
docker run --rm -i --network host grafana/k6 run - < tests/k6/load.js
```

## Scripts

| Script | Objetivo | NFR validado |
|--------|----------|-------------|
| `smoke.js` | Sanidade — 2 VUs, 30s | Todos os endpoints respondem |
| `load.js` | Carga nominal — rampa até 50 req/s | NFR-02, NFR-03 |
| `stress.js` | Stress — até 200 req/s | Degradação graceful, rate limit 429 |
| `idempotencia.js` | Exactly-once sob concorrência | Garantia de idempotência |

## Execução

```bash
# Serviços rodando via docker compose --profile app up

# 1. Smoke (sempre primeiro)
k6 run tests/k6/smoke.js

# 2. Carga nominal (valida NFR-02 e NFR-03)
k6 run tests/k6/load.js

# 3. Stress
k6 run tests/k6/stress.js

# 4. Idempotência
k6 run tests/k6/idempotencia.js

# Apontando para Traefik HTTPS
k6 run -e BASE_URL=https://localhost:8443 --insecure-skip-tls-verify tests/k6/load.js

# Com output para Grafana (requer k6 + InfluxDB ou Prometheus remote write)
k6 run --out influxdb=http://localhost:8086/k6 tests/k6/load.js
```

## Thresholds (NFRs)

| Métrica | Threshold | NFR |
|---------|-----------|-----|
| `http_req_failed` (consolidado) | `< 5%` | NFR-02 |
| `consolidado_latencia` p99 | `< 200ms` | NFR-03 (cache Redis) |
| `lancamentos_latencia` p95 | `< 1000ms` | Escrita com transação + evento |
| `violacoes_idempotencia` | `= 0` | Garantia exactly-once |
| `sem 5xx` (stress) | `100%` | Degradação graceful |
