---
tags:
  - implementacao
  - resiliencia
  - chaos-engineering
  - observabilidade
---

# Chaos Engineering

**Perspectiva:** ⚙️ Arquiteto de Tecnologia · 👁️ Arquiteto de Observabilidade  
**Status:** Executado — todos os 5 experimentos concluídos em 2026-05-17  
**NFRs validados:** NFR-01 (lançamentos independente do consolidado) · NFR-02 (50 req/s, ≤ 5% erro)

---

## Por que Chaos Engineering aqui

Os padrões de resiliência — Resilience4j no publisher RabbitMQ e `CacheErrorHandler` no Redis — foram **decididos com base em hipóteses** sobre comportamento sob falha. O Chaos Engineering converte essas hipóteses em evidências mensuráveis.

O ciclo adotado é o de Chaos Engineering clássico:

```
1. Definir estado estável (steady state)
2. Formular hipótese sobre comportamento sob falha
3. Injetar falha controlada
4. Observar via Grafana / Loki / k6
5. Restaurar e confirmar recuperação automática
```

---

## Ferramenta: Pumba

**Pumba** opera sobre o Docker Engine — sem agente, sem sidecar, sem mudança de infraestrutura. Adequado para o ambiente local com `docker compose`.

```bash
docker pull gaiaadm/pumba
```

> **Nota:** `gaiaadm/pumba netem` requer uma imagem auxiliar com `iproute2` (`tc`). A imagem `ghcr.io/alexei-led/pumba-alpine-nettools` não está disponível publicamente. Utilizamos imagem customizada local:
>
> ```dockerfile
> # tests/caos/Dockerfile.tc-tools
> FROM alpine:3.21
> RUN apk add --no-cache iproute2
> ```
>
> ```bash
> docker build -f tests/caos/Dockerfile.tc-tools -t pumba-tc-tools:local .
> ```

Capacidades relevantes para este projeto:

| Comando Pumba | Efeito |
|---------------|--------|
| `kill` | Mata o container (SIGKILL) |
| `stop` | Para o container graciosamente |
| `netem delay` | Adiciona latência de rede |
| `netem loss` | Simula perda de pacotes |
| `netem corrupt` | Corrompe pacotes |

---

## Carga sintética: k6

```bash
# Script principal — 20 req/s consolidado + 5 VUs lançamentos por 5 minutos
k6 run tests/k6/chaos.js --out json=/tmp/k6-resultado.json
```

Thresholds configurados (tolerância maior que o load test normal):

| Serviço | Threshold |
|---------|-----------|
| consolidado | `http_req_failed < 5%` |
| lancamentos | `http_req_failed < 1%` |

---

## Estado Estável (Steady State)

Antes de injetar qualquer falha, o sistema deve exibir o seguinte comportamento de referência:

| Métrica | Valor esperado | Fonte |
|---------|---------------|-------|
| `http_req_failed` em `/lancamentos/registros` | < 1% | k6 |
| `http_req_failed` em `/consolidacao/saldo/{data}` | < 5% | k6 |
| `consolidado_latencia p99` | < 200ms (cache quente) | k6 custom metric |
| `cache_redis_errors_total` | 0 | Prometheus |
| `resilience4j_circuitbreaker_state` | `closed` (0) | Prometheus |
| Ambos os serviços respondendo `/health/ready` | `200 {"status":"UP"}` | curl |

---

## Resultados

### Experimento 1 — Redis Indisponível

**Hipótese:** quando o Redis cai, o consolidado continua respondendo via banco de dados com latência maior, sem retornar erros ao cliente.

**NFR validado:** NFR-02 — tolerância a falha de cache sem degradação de disponibilidade.

```bash
# Pumba mata o Redis a cada 15s enquanto k6 roda
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba --interval 15s kill data-redis
```

| Observação | Resultado |
|------------|-----------|
| `http_req_failed` no consolidado | ✅ < 5% |
| `consolidado_latencia p99` | ✅ sobe (banco > cache), mas responde |
| `CacheErrorHandler` absorve exceções Redis | ✅ sem 500 para o cliente |
| Após restart Redis: latência normaliza | ✅ recuperação automática |

**Hipótese confirmada.** O `CacheErrorHandler` silencia falhas de cache e redireciona para o banco sem propagar exceção para o cliente.

---

### Experimento 2 — RabbitMQ Indisponível

**Hipótese:** quando o RabbitMQ cai, o Resilience4j faz 3 retries com backoff e absorve a falha — o lançamento é salvo no banco e o cliente recebe 201. O circuito abre após falhas consecutivas.

**NFR validado:** NFR-01 — serviço de lançamentos disponível independentemente da infraestrutura de mensageria.

```bash
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba stop data-rabbitmq
```

| Observação | Resultado |
|------------|-----------|
| `resilience4j_retry_calls_total{kind="with_retry"}` | ✅ aumenta — retries acontecendo |
| `resilience4j_circuitbreaker_state{state="open"}` | ✅ abre após falhas consecutivas |
| `POST /lancamentos/registros` retorna | ✅ 201 — lançamento salvo via Outbox |
| Após restart RabbitMQ: circuito fecha | ✅ `half-open` → `closed` |

**Hipótese confirmada.** O Outbox Pattern garante que o lançamento é persistido mesmo com o broker fora; o Resilience4j gerencia o ciclo de vida do circuito.

---

### Experimento 3 — Redis Lento (latência de rede)

**Hipótese:** quando o Redis responde em > 200ms, o timeout do Redis (`spring.data.redis.timeout=200ms`) cancela a operação e o fallback para o banco é acionado — sem bloquear threads além do timeout configurado.

```bash
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba netem \
    --duration 120s \
    --tc-image pumba-tc-tools:local \
    delay --time 500 \
    data-redis
```

**Resultados k6 (9 286 requisições, 5 minutos):**

| Métrica | Valor | Threshold |
|---------|-------|-----------|
| `http_req_failed` | **0.29%** | < 5% ✅ |
| `consolidado_latencia p50` | 27ms | — |
| `consolidado_latencia p95` | 122ms | — |
| `consolidado_latencia p99` | **248ms** | — |
| `lancamentos_latencia p99` | 134ms | — |
| `chaos_erros_total` | 3 | — |

**Hipótese confirmada.** Com 500ms de latência injetada no Redis, o timeout de 200ms foi atingido, o fallback para banco foi acionado e o p99 do consolidado subiu para 248ms — aceitável e dentro do threshold de disponibilidade. Virtual threads absorveram o aumento de concorrência sem saturar thread pool.

---

### Experimento 4 — Consolidado Indisponível (NFR-01)

**Hipótese:** derrubar o consolidado não afeta o serviço de lançamentos de nenhuma forma.

```bash
docker compose stop consolidado
```

| Observação | Resultado |
|------------|-----------|
| `POST /lancamentos/registros` | ✅ 201 — indiferente ao consolidado |
| `GET /lancamentos/registros/{id}` | ✅ 200 — indiferente ao consolidado |
| Logs do lancamentos | ✅ zero menção ao consolidado |
| Após restart consolidado | ✅ processa eventos acumulados na fila |

**Hipótese confirmada.** Este é o **NFR-01 em ação** — a premissa central do desafio. Lançamentos e consolidado são serviços independentes; a falha de um não propaga para o outro.

---

### Experimento 5 — Falha em Cascata (combinado)

**Hipótese:** com Redis e RabbitMQ simultaneamente indisponíveis, o sistema degrada de forma previsível: lançamentos são aceitos (Outbox + circuit breaker), consolidado serve do banco (cache fallback), sem 500 para o cliente.

```bash
# Após 15s de steady state com k6 rodando:
docker stop data-redis data-rabbitmq

# Após 2 minutos:
docker start data-redis data-rabbitmq
```

**Resultados k6 (62 994 requisições, 5 minutos):**

| Métrica | Valor | Threshold |
|---------|-------|-----------|
| `http_req_failed` global | **0.05%** | < 5% ✅ |
| `consolidado_latencia p50` | 408ms | — |
| `consolidado_latencia p95` | 437ms | — |
| `consolidado_latencia p99` | **470ms** | — |
| `lancamentos_latencia p50` | 21ms | — |
| `lancamentos_latencia p99` | **82ms** | — |
| `chaos_erros_total` | 4 | — |

**Hipótese confirmada.** Com ambas as dependências fora simultaneamente:

- **Lançamentos permanece rápido** (p99=82ms) — Outbox persiste no banco, circuit breaker absorve o broker
- **Consolidado degrada graciosamente** (p99=470ms) — fallback para banco, latência maior mas disponível
- **Taxa de erro global: 0.05%** — muito abaixo do threshold de 5%

A degradação é **previsível, observável e recuperável** — os três pilares do Chaos Engineering.

---

## Sumário dos Resultados

| Experimento | Cenário | Taxa de erro | Resultado |
|-------------|---------|-------------|-----------|
| 1 | Redis kill (15s interval) | < 5% | ✅ Hipótese confirmada |
| 2 | RabbitMQ stop | < 1% lançamentos | ✅ Hipótese confirmada |
| 3 | Redis netem 500ms delay | **0.29%** | ✅ Hipótese confirmada |
| 4 | Consolidado stop | < 1% lançamentos | ✅ NFR-01 confirmado |
| 5 | Redis + RabbitMQ combinado | **0.05%** | ✅ Hipótese confirmada |

**Todos os NFRs críticos validados experimentalmente.**

---

## Sequência de Execução

```
1. Build imagem tc-tools local          ← uma vez só
2. Build e deploy local (docker compose)
3. Estabelecer steady state (k6 2min + health checks)
4. Experimento 1 — Redis kill            → validar CacheErrorHandler
5. Restaurar Redis, aguardar steady state
6. Experimento 2 — RabbitMQ stop         → validar Outbox + circuit breaker
7. Restaurar RabbitMQ, aguardar steady state
8. Experimento 3 — Redis netem 500ms     → validar timeout + fallback
9. Restaurar rede, aguardar steady state
10. Experimento 4 — Consolidado stop     → validar NFR-01
11. Restaurar consolidado
12. Experimento 5 — Falha combinada      → validar degradação previsível
```
