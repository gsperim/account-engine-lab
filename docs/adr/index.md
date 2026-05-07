# Decisões Arquiteturais (ADRs)

Registro das decisões arquiteturais relevantes do projeto, com contexto, alternativas consideradas e consequências.

---

## Índice

| ADR | Título | Status | Data |
|-----|--------|--------|------|
| [ADR-001](ADR-001-padrao-arquitetural.md) | Padrão Arquitetural: Microserviços Orientados a Eventos | Aceito | 2026-05-07 |
| [ADR-002](ADR-002-message-broker.md) | Message Broker: RabbitMQ | Aceito | 2026-05-07 |
| [ADR-003](ADR-003-outbox-pattern.md) | Garantia de Entrega: Transactional Outbox Pattern | Aceito | 2026-05-07 |
| [ADR-004](ADR-004-jwt-validacao-local.md) | Validação de Tokens: JWT com Validação Local via JWKS | Aceito | 2026-05-07 |
| [ADR-005](ADR-005-protocolo-comunicacao-interna.md) | Protocolo de Comunicação Interna: REST sobre gRPC | Aceito | 2026-05-07 |

> Decisões sobre ferramentas e stack de desenvolvimento estão em [Ferramentas e Stack](../stack.md).

---

## Convenção

Cada ADR segue a estrutura:

- **Contexto** — situação que originou a necessidade de decisão
- **Decisão** — o que foi decidido e por quê
- **Alternativas Consideradas** — o que foi avaliado e descartado
- **Consequências** — impactos positivos e trade-offs

**Status possíveis:** Proposto · Aceito · Depreciado · Substituído

---

## Leituras de Referência

Materiais externos que embasam ou influenciam decisões arquiteturais deste projeto.

| Tema | Recurso |
|------|---------|
| Estratégias de cache com Redis (técnicas: Cache-Aside, Write-Through, Read-Through, TTL, eviction) | [Database Caching Strategies Using Redis — AWS Whitepaper](https://docs.aws.amazon.com/pdfs/whitepapers/latest/database-caching-strategies-using-redis/database-caching-strategies-using-redis.pdf) |
| Instrumentação de observabilidade — padrão unificado para traces, métricas e logs (D-10, D-11) | [OpenTelemetry — especificação e SDKs](https://opentelemetry.io/docs/) |
| Rastreamento distribuído — visualização de traces em sistemas de microserviços (D-10) | [Jaeger — distributed tracing](https://www.jaegertracing.io/docs/) |
| Padrão de campos de log estruturado — schema de referência para JSON logs (D-11) | [Elastic Common Schema (ECS)](https://www.elastic.co/guide/en/ecs/current/index.html) |
| Pipeline de linhagem de dados e catálogo — analytics sobre dados operacionais (D-09) | [OpenMetadata — documentação](https://docs.open-metadata.org/) |
