# Decisões Arquiteturais do Sistema

Decisões de arquitetura do sistema **independentes de provedor de nuvem** — padrões, protocolos, mecanismos de resiliência e escolhas de plataforma local.

> As decisões de **implementação em AWS** (cloud provider, API Gateway gerenciado, segurança, operacional) estão documentadas separadamente em [Decisões de Nuvem](../cloud/adrs.md).

---

## Índice

| ADR | Decisão | Status | Data |
|-----|---------|--------|------|
| [ADR-001](ADR-001-padrao-arquitetural.md) | Padrão Arquitetural: Microserviços Orientados a Eventos | Aceito | 2026-05-07 |
| [ADR-002](ADR-002-message-broker.md) | Message Broker: RabbitMQ | Aceito | 2026-05-07 |
| [ADR-003](ADR-003-outbox-pattern.md) | Garantia de Entrega: Transactional Outbox Pattern | Aceito | 2026-05-07 |
| [ADR-004](ADR-004-jwt-validacao-local.md) | Validação de Tokens: JWT com Validação Local via JWKS | Aceito | 2026-05-07 |
| [ADR-005](ADR-005-protocolo-comunicacao-interna.md) | Protocolo de Comunicação Interna: REST sobre gRPC | Aceito | 2026-05-07 |
| [ADR-006](ADR-006-container-runtime.md) | Container Runtime: docker-compose local, Kubernetes para produção | Aceito | 2026-05-07 |
| [ADR-007](ADR-007-api-gateway.md) | API Gateway Local: Traefik — TLS, rate limiting e service discovery | Aceito | 2026-05-08 |

---

## Convenção

Cada ADR segue a estrutura:

- **Contexto** — situação que originou a necessidade de decisão
- **Decisão** — o que foi decidido e por quê
- **Alternativas Consideradas** — o que foi avaliado e descartado
- **Consequências** — impactos positivos e trade-offs

**Status possíveis:** Proposto · Aceito · Depreciado · Substituído

> Decisões sobre ferramentas e stack de desenvolvimento estão em [Ferramentas e Stack](../stack.md).

---

## Leituras de Referência

| Tema | Recurso |
|------|---------|
| Estratégias de cache com Redis (Cache-Aside, Write-Through, TTL, eviction) | [Database Caching Strategies Using Redis — AWS Whitepaper](https://docs.aws.amazon.com/pdfs/whitepapers/latest/database-caching-strategies-using-redis/database-caching-strategies-using-redis.pdf) |
| Instrumentação de observabilidade — padrão unificado para traces, métricas e logs | [OpenTelemetry — especificação e SDKs](https://opentelemetry.io/docs/) |
| Rastreamento distribuído — visualização de traces em microserviços | [Jaeger — distributed tracing](https://www.jaegertracing.io/docs/) |
| Schema de referência para JSON logs estruturados | [Elastic Common Schema (ECS)](https://www.elastic.co/guide/en/ecs/current/index.html) |
| Pipeline de linhagem de dados e catálogo | [OpenMetadata — documentação](https://docs.open-metadata.org/) |
