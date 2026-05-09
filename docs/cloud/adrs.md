# Decisões de Nuvem (ADRs)

Decisões arquiteturais específicas da implementação em AWS. Complementam os [ADRs do Sistema](../adr/index.md), que documentam as decisões de arquitetura independentes de provedor.

---

## Por que ADRs separados para nuvem?

Um ADR de sistema responde: *"Por que microserviços? Por que RabbitMQ? Por que Outbox?"*  
Um ADR de nuvem responde: *"Por que AWS e não GCP? Por que API Gateway HTTP API e não REST API? Por que Karpenter?"*

São decisões de natureza diferente — as primeiras guiam o design do software, as segundas guiam onde e como ele roda. Separá-las permite que a documentação do sistema permaneça relevante mesmo que a plataforma de nuvem mude no futuro.

---

## Índice

| ADR | Decisão | Status | Data |
|-----|---------|--------|------|
| [ADR-008](../adr/ADR-008-cloud-provider.md) | Provedor de Nuvem: AWS `sa-east-1` | Aceito | 2026-05-08 |
| [ADR-009](../adr/ADR-009-api-gateway-producao.md) | API Gateway de Produção: AWS API Gateway HTTP API + CloudFront | Aceito | 2026-05-08 |
| [ADR-010](../adr/ADR-010-seguranca.md) | Segurança Multicamada (Defense in Depth): WAF, mTLS, KMS, GuardDuty, SIEM | Aceito | 2026-05-08 |
| [ADR-011](../adr/ADR-011-excelencia-operacional.md) | Excelência Operacional: RDS Proxy, VPC Endpoints, ECR, Karpenter, Backup, Monitoring, FinOps | Aceito | 2026-05-08 |

---

## Mapa de Decisões por Camada

```
Camada              Decisão                         ADR
──────────────────────────────────────────────────────────
Provedor            AWS sa-east-1                   ADR-008
Borda               CloudFront + WAF v2 + mTLS       ADR-009, ADR-010
Autenticação        API Gateway JWT Authorizer       ADR-009
Compute             EKS + Karpenter (On-Demand+Spot) ADR-011
Connection Pooling  RDS Proxy                        ADR-011
Dados em Repouso    KMS CMK + Secrets Manager        ADR-010
Rede                VPC Endpoints (PrivateLink)      ADR-011
Supply Chain        ECR + Inspector v2               ADR-011
DR                  AWS Backup cross-region          ADR-011
Detecção            GuardDuty + Security Hub         ADR-010
FinOps              Budgets + Cost Anomaly           ADR-011
```
