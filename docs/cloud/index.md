# Implementação em Nuvem — AWS

**Perspectiva:** 🏗️ Arquiteto de Infraestrutura · 🧩 Arquiteto de Soluções  
**Nota:** Este capítulo documenta *como* o sistema é implantado em produção na AWS. As decisões de *o que o sistema faz* — padrão arquitetural, mensageria, contratos — estão nos [ADRs do Sistema](../adr/index.md).

---

## Por que documentar separado?

O sistema foi projetado **cloud-agnostic**: os serviços comunicam via variáveis de ambiente, os contratos são definidos em OpenAPI e AsyncAPI, e o docker-compose local é funcionalmente equivalente ao ambiente de produção. Mudar de provedor de nuvem exigiria ajustar variáveis e manifests, não o código da aplicação.

As decisões de nuvem são portanto uma **camada separada** — opções de implementação dentro de um envelope de requisitos que o sistema define (NFR-02, NFR-04, NFR-08).

---

## Estratégia de Nuvem

**Provedor:** AWS `sa-east-1` (São Paulo) — única região de hyperscaler com data center físico no Brasil, conforme exigência de latência e LGPD. Detalhes em [ADR-008](../adr/ADR-008-cloud-provider.md).

**Modelo de implantação:**

| Serviço local | Recurso AWS |
|---|---|
| `docker compose up` | `terraform apply` + `helm install` |
| `traefik` | CloudFront + WAF v2 + API Gateway HTTP API |
| `postgres:16` | RDS PostgreSQL 16 Multi-AZ + RDS Proxy |
| `redis:7-alpine` | ElastiCache Redis 7 (primary + replica) |
| `rabbitmq:3.13-management` | RabbitMQ self-hosted no EKS (Helm bitnami) |
| imagens locais | Amazon ECR (privado, imutável, scan on push) |

**Infraestrutura como Código:** toda a infraestrutura de produção está em [`/terraform`](../../terraform/README.md) — 21 arquivos `.tf` organizados por responsabilidade. O estado é gerenciado em S3 com locking via DynamoDB.

---

## Camadas da Arquitetura AWS

```mermaid
architecture-beta

    group borda(cloud)[Borda]
    group cluster(server)[EKS Cluster]
    group dados(database)[Dados]
    group segops(disk)[Seguranca e Ops]

    service inet(internet)[Internet]

    service r53(server)[Route 53] in borda
    service cf(cloud)[CloudFront e WAF v2] in borda
    service apigw(server)[API Gateway JWT mTLS] in borda

    service alb(server)[ALB Interno] in cluster
    service lanc(server)[lancamentos] in cluster
    service cons(server)[consolidado] in cluster
    service rmq(database)[RabbitMQ x3] in cluster

    service proxy_l(database)[Proxy lancamentos] in dados
    service rds_l(database)[RDS lancamentos] in dados
    service proxy_c(database)[Proxy consolidado] in dados
    service rds_c(database)[RDS consolidado] in dados
    service redis(database)[ElastiCache Redis] in dados

    service kms(disk)[KMS CMK e Secrets] in segops
    service detect(server)[GuardDuty e Security Hub] in segops
    service observe(disk)[CloudWatch e Backup] in segops

    inet:R --> L:r53
    r53:R --> L:cf
    cf:R --> L:apigw
    apigw:B --> T:alb
    alb:B --> T:lanc
    alb:B --> T:cons
    lanc:B --> T:proxy_l
    cons:B --> T:proxy_c
    proxy_l:B --> T:rds_l
    proxy_c:B --> T:rds_c
    cons:R --> L:redis
    lanc:R --> L:rmq
    cons:T --> B:rmq
    rds_l:R --> L:kms
    rds_c:R --> L:kms
    alb:R --> L:observe
    cons:R --> L:detect
```

---

## Documentação desta Seção

| Documento | Conteúdo |
|-----------|----------|
| [Decisões de Nuvem (ADRs)](adrs.md) | ADR-008 a ADR-011 — justificativas das escolhas AWS |
| [Estimativa de Custos](custos.md) | Breakdown por serviço, comparativos e como reproduzir via Infracost |
| [Topologia de Infraestrutura](../infraestrutura/topologia.md) | Diagrama de redes do ambiente local (docker-compose) |

---

## Evolução Planejada

| Fase | Adição |
|------|--------|
| Etapa 8 (Pipeline) | IRSA por pod, Karpenter NodePools, manifestos Kubernetes, CI/CD com Infracost diff em PRs |
| Pós-entrega | Amazon Security Lake (SIEM completo), Shield Advanced, multi-conta (prod/staging/auditoria) |
