---
tags:
  - arquitetura
  - c4
---

# Diagramas C4

**Perspectiva:** 🧩 Arquiteto de Soluções  
**Níveis:** C4 L1 · L2 · L3 (Components) · L5 (Deployment)  
**Fonte:** [`structurizr/workspace.dsl`](../../structurizr/workspace.dsl) · visualização interativa: `docker compose up structurizr` → http://localhost:8080

---

## C4 L1 — Contexto do Sistema

**O que mostra:** o sistema como uma caixa preta, seus usuários diretos (Caixa, Gestor, PDV) e os sistemas externos com os quais se integra (Identity Provider, Plataforma de Observabilidade).

[![Diagrama de Contexto do Sistema](assets/contexto.png)](assets/contexto.png)

---

## C4 L2 — Containers do Sistema de Negócio

**O que mostra:** os containers internos — Serviço de Lançamentos, Outbox Relay, Serviço de Consolidação, bancos de dados, cache, broker — e como se comunicam entre si e com os sistemas externos.

[![Diagrama de Containers](assets/containers.png)](assets/containers.png)

[![Legenda](assets/containers-key.png)](assets/containers-key.png)

---

## C4 L2 — Plataforma de Observabilidade

**O que mostra:** os containers da stack PLG + OTEL — OTEL Collector, Prometheus, Alertmanager, Loki, Promtail, Tempo, Pyroscope, Grafana, Blackbox Exporter — e como recebem telemetria dos serviços de negócio.

[![Diagrama de Observabilidade](assets/observabilidade-containers.png)](assets/observabilidade-containers.png)

[![Legenda](assets/observabilidade-containers-key.png)](assets/observabilidade-containers-key.png)

---

## C4 L3 — Components (Arquitetura Hexagonal)

> Exportar via Structurizr Lite após `docker compose up structurizr`: views `lancamentos-components`, `consolidado-components` e `outbox-relay-components`.

**O que mostra:** os componentes internos de cada serviço organizados nas camadas da Arquitetura Hexagonal:

| Camada | Cor | Exemplos |
|--------|-----|---------|
| **Adapters IN** | Amarelo | `LancamentoController`, `LancamentoEventoConsumer`, `AdminController` |
| **Application** | Verde | `RegistrarLancamentoService`, `ProcessarLancamentoService`, `ReconciliacaoDiariaJob` |
| **Adapters OUT** | Rosa | `LancamentoRepositoryAdapter`, `SaldoConsolidadoRepositoryAdapter`, `LancamentosGatewayAdapter` |

---

## C4 L5 — Deployment

> Exportar via Structurizr Lite: views `deployment-dev` e `deployment-prod`.

**Ambiente de Desenvolvimento (Docker Compose):**

| Container | Tecnologia | Porta |
|-----------|-----------|-------|
| traefik | Traefik 3 | 8090 · 8443 · 8091 |
| keycloak | Keycloak 26 | 8180 |
| lancamentos | JVM · Spring Boot | — |
| outbox-relay | JVM · Spring Boot | — |
| consolidado | JVM · Spring Boot | — |
| postgres-lancamentos | PostgreSQL 16 | — (internal) |
| postgres-consolidado | PostgreSQL 16 | — (internal) |
| redis | Redis 7 · AOF | — (internal) |
| rabbitmq | RabbitMQ 3.13 | 15672 |
| otel-collector + PLG | stack completa | 9090 · 3000 |

**Ambiente de Produção (AWS):**

| Componente AWS | Mapeia para | Detalhe |
|----------------|-------------|---------|
| CloudFront + API Gateway HTTP API | API Gateway | WAF v2 · rate limiting |
| EKS — lancamentos Deployment | Serviço de Lançamentos | 2 réplicas |
| EKS — consolidado Deployment | Serviço de Consolidação | 2–10 réplicas (HPA) |
| EKS — outbox-relay Deployment | Outbox Relay | 1 réplica |
| RDS PostgreSQL 16 (×2) | Bancos por serviço | Multi-AZ · RDS Proxy |
| ElastiCache Redis 7 | Cache | cluster mode · KMS CMK |
| Amazon MQ RabbitMQ | Message Broker | AMQPS · Multi-AZ |
| Cognito / IdP corporativo | Identity Provider | substitui Keycloak local |

---

## Fonte canônica

```bash
docker compose up structurizr
# Acesse: http://localhost:8080
# Exporte: menu Diagrams → Export → PNG
```

O DSL em [`structurizr/workspace.dsl`](../../structurizr/workspace.dsl) é a fonte de verdade. Os PNGs nesta página são exportações pontuais e podem ficar desatualizados se o DSL for alterado sem re-exportar.
