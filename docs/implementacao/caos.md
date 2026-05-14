---
tags:
  - implementacao
  - resiliencia
  - chaos-engineering
  - observabilidade
---

# Chaos Engineering

**Perspectiva:** ⚙️ Arquiteto de Tecnologia · 👁️ Arquiteto de Observabilidade  
**Status:** Planejado — execução após implementação de Resilience4j e Redis fallback  
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
# Instalação
docker pull gaiaadm/pumba

# Exemplo de uso
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba kill --interval 30s data-redis
```

Capacidades relevantes para este projeto:

| Comando Pumba | Efeito |
|---------------|--------|
| `kill` | Mata o container (SIGKILL) |
| `stop` | Para o container graciosamente |
| `netem delay` | Adiciona latência de rede |
| `netem loss` | Simula perda de pacotes |
| `netem corrupt` | Corrompe pacotes |

---

## Estado Estável (Steady State)

Antes de injetar qualquer falha, o sistema deve exibir o seguinte comportamento de referência durante 2 minutos de carga com k6:

| Métrica | Valor esperado | Fonte |
|---------|---------------|-------|
| `http_req_failed` em `/lancamentos/registros` | < 1% | k6 |
| `http_req_failed` em `/consolidacao/saldo/{data}` | < 5% | k6 |
| `consolidado_latencia p99` | < 200ms (cache quente) | k6 custom metric |
| `cache_redis_errors_total` | 0 | Prometheus |
| `resilience4j_circuitbreaker_state` | `closed` (0) | Prometheus |
| Ambos os serviços respondendo `/health/ready` | `200 {"status":"UP"}` | curl |

```bash
# Estabelecer steady state
k6 run --duration 2m tests/k6/load.js
```

---

## Experimentos

### Experimento 1 — Redis Indisponível

**Hipótese:** quando o Redis cai, o consolidado continua respondendo via banco de dados com latência maior, sem retornar erros ao cliente.

**NFR validado:** NFR-02 — tolerância a falha de cache sem degradação de disponibilidade.

```bash
# Enquanto k6 roda
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba kill --interval 15s data-redis
```

| O que observar | Onde | Resultado esperado |
|---------------|------|-------------------|
| `cache_redis_errors_total` aumenta | Grafana / Prometheus | ✅ métrica de alerta ativa |
| `http_req_failed` no consolidado | k6 | < 5% (mesma tolerância) |
| `consolidado_latencia p99` | k6 | sobe (banco > cache), mas responde |
| Alerta `RedisCacheFallbackAtivo` | Telegram | dispara em < 2min |
| Após restart Redis: latência volta | Grafana | recuperação automática |

---

### Experimento 2 — RabbitMQ Indisponível

**Hipótese:** quando o RabbitMQ cai, o Resilience4j faz 3 retries com backoff e absorve a falha — o lançamento é salvo no banco e o cliente recebe 201. O circuito abre após falhas consecutivas.

**NFR validado:** NFR-01 — serviço de lançamentos disponível independentemente da infraestrutura de mensageria.

```bash
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba stop data-rabbitmq
```

| O que observar | Onde | Resultado esperado |
|---------------|------|-------------------|
| `resilience4j_retry_calls_total{kind="with_retry"}` | Prometheus | aumenta — retries acontecendo |
| `resilience4j_circuitbreaker_state{state="open"}` | Prometheus | abre após N falhas consecutivas |
| `POST /lancamentos/registros` retorna | k6 | 201 (lançamento salvo, evento absorvido) |
| Alerta `CircuitBreakerAberto` | Telegram | dispara imediatamente (sem `for`) |
| Lançamentos no banco | PostgreSQL | contagem bate com k6 |
| Após restart RabbitMQ: circuito fecha | Grafana | `half-open` → `closed` |

---

### Experimento 3 — Redis Lento (latência de rede)

**Hipótese:** quando o Redis responde em > 200ms, o `TimeLimiter` cancela a operação e o fallback para o banco é acionado — sem bloquear threads além do timeout configurado.

```bash
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba netem --duration 120s \
  --tc-image ghcr.io/alexei-led/pumba-alpine-nettools:latest \
  delay --time 500 data-redis
```

| O que observar | Onde | Resultado esperado |
|---------------|------|-------------------|
| `consolidado_latencia p99` | k6 | não ultrapassa timeout + margem |
| `http_req_failed` | k6 | < 5% |
| Thread pool não esgota | Grafana / JVM metrics | virtual threads absorvem |
| `cache_redis_errors_total` | Prometheus | aumenta (fallback ativo) |

---

### Experimento 4 — Consolidado Indisponível (NFR-01)

**Hipótese:** derrubar o consolidado não afeta o serviço de lançamentos de nenhuma forma.

```bash
docker compose stop consolidado
```

| O que observar | Onde | Resultado esperado |
|---------------|------|-------------------|
| `POST /lancamentos/registros` | k6 | 201 — indiferente ao consolidado |
| `GET /lancamentos/registros/{id}` | k6 | 200 — indiferente ao consolidado |
| Logs do lancamentos | Loki | zero menção ao consolidado |
| Após restart consolidado | Grafana | processa eventos acumulados na fila |

Este é o **NFR-01 em ação** — a premissa central do desafio.

---

### Experimento 5 — Falha em Cascata (combinado)

**Hipótese:** com Redis e RabbitMQ simultaneamente indisponíveis, o sistema degrada de forma previsível: lançamentos são aceitos (via circuit breaker), consolidado serve do banco (via fallback), sem 500 para o cliente.

```bash
# Terminal 1 — mata Redis
docker compose stop redis

# Terminal 2 — mata RabbitMQ
docker compose stop rabbitmq

# Terminal 3 — k6 rodando
k6 run tests/k6/load.js
```

| O que observar | Onde | Resultado esperado |
|---------------|------|-------------------|
| Ambos os circuit breakers | Prometheus | `open` |
| Ambos os alertas | Telegram | disparados |
| `POST /lancamentos/registros` | k6 | 201 |
| `GET /consolidacao/saldo/{data}` | k6 | 200 (banco) |
| `http_req_failed` global | k6 | < 5% |

---

## Sequência de Execução

```
1. Implementar Resilience4j + CacheErrorHandler  ← próximo passo
2. Build e deploy local (docker compose)
3. Estabelecer steady state (k6 2min + Grafana)
4. Experimento 1 — Redis kill            → validar fallback
5. Restaurar Redis, aguardar steady state
6. Experimento 2 — RabbitMQ stop         → validar circuit breaker
7. Restaurar RabbitMQ, aguardar steady state
8. Experimento 3 — Redis netem 500ms     → validar TimeLimiter
9. Restaurar rede, aguardar steady state
10. Experimento 4 — Consolidado stop     → validar NFR-01
11. Restaurar consolidado
12. Experimento 5 — Falha combinada      → validar degradação previsível
```

---

## O que Registrar como Evidência

Para cada experimento, capturar:

- **Screenshot Grafana** — circuit breaker state + latência durante a falha
- **Output k6** — `http_req_failed` e `p99` durante o caos
- **Screenshot Telegram** — alerta disparado e alerta de recuperação
- **Query Loki** — `{service="lancamentos"} | json | correlation_id="..."` mostrando o fluxo completo

Essas evidências documentam que o sistema falha de forma **previsível, observável e recuperável** — os três pilares do Chaos Engineering.
