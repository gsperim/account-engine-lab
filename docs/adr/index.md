# Decisões Arquiteturais

Registro de todas as decisões arquiteturais do projeto — tanto as decisões sobre **o sistema** (cloud-agnostic) quanto as decisões sobre **como ele roda em produção** (AWS).

---

## Decisões do Sistema

Decisões independentes de provedor de nuvem — padrões, protocolos e mecanismos de resiliência.

| ADR | Decisão | Status | Data |
|-----|---------|--------|------|
| [ADR-001](ADR-001-padrao-arquitetural.md) | Padrão Arquitetural: Microserviços Orientados a Eventos | Aceito | 2026-05-07 |
| [ADR-002](ADR-002-message-broker.md) | Message Broker: RabbitMQ | Aceito | 2026-05-07 |
| [ADR-003](ADR-003-outbox-pattern.md) | Garantia de Entrega: Transactional Outbox Pattern | Aceito | 2026-05-07 |
| [ADR-004](ADR-004-jwt-validacao-local.md) | Validação de Tokens: JWT com Validação Local via JWKS | Aceito | 2026-05-07 |
| [ADR-005](ADR-005-protocolo-comunicacao-interna.md) | Protocolo de Comunicação Interna: REST sobre gRPC | Aceito | 2026-05-07 |
| [ADR-006](ADR-006-container-runtime.md) | Container Runtime: docker-compose local, Kubernetes para produção | Aceito | 2026-05-07 |
| [ADR-007](ADR-007-api-gateway.md) | API Gateway Local: Traefik — TLS, rate limiting e service discovery | Aceito | 2026-05-08 |

## Decisões de Nuvem (AWS)

Decisões sobre a implementação de produção na AWS — separadas para preservar a natureza cloud-agnostic do sistema.

| ADR | Decisão | Status | Data |
|-----|---------|--------|------|
| [ADR-008](ADR-008-cloud-provider.md) | Provedor de Nuvem: AWS `sa-east-1` | Aceito | 2026-05-08 |
| [ADR-009](ADR-009-api-gateway-producao.md) | API Gateway de Produção: HTTP API + CloudFront | Aceito | 2026-05-08 |
| [ADR-010](ADR-010-seguranca.md) | Segurança em Profundidade: WAF, mTLS, KMS, GuardDuty, SIEM | Aceito | 2026-05-08 |
| [ADR-011](ADR-011-excelencia-operacional.md) | Excelência Operacional: RDS Proxy, Karpenter, Backup, FinOps | Aceito | 2026-05-08 |

---

## Convenção

Cada ADR segue a estrutura: **Contexto** → **Decisão** → **Alternativas Consideradas** → **Trade-offs** → **Consequências**

**Status possíveis:** Proposto · Aceito · Depreciado · Substituído

> Decisões sobre ferramentas e stack de desenvolvimento estão em [Stack e Ferramentas](../stack.md).

---

## Leituras de Referência

| Tema | Recurso |
|------|---------|
| Estratégias de cache com Redis | [Database Caching Strategies Using Redis — AWS Whitepaper](https://docs.aws.amazon.com/pdfs/whitepapers/latest/database-caching-strategies-using-redis/database-caching-strategies-using-redis.pdf) |
| Instrumentação de observabilidade | [OpenTelemetry — especificação e SDKs](https://opentelemetry.io/docs/) |
| Rastreamento distribuído | [Jaeger — distributed tracing](https://www.jaegertracing.io/docs/) |
| Schema de referência para JSON logs | [Elastic Common Schema (ECS)](https://www.elastic.co/guide/en/ecs/current/index.html) |
| Pipeline de linhagem de dados | [OpenMetadata — documentação](https://docs.open-metadata.org/) |
