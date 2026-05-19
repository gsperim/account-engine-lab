# Estimativa de Custos de Infraestrutura

**Perspectiva:** 🏗️ Arquiteto de Infraestrutura · ⚙️ Arquiteto de Tecnologia  
**Requisito de origem:** [NFR-02](../negocio/requisitos.md#nfr-02) (50 req/s), [NFR-04](../negocio/requisitos.md#nfr-04) (99,9% disponibilidade), [NFR-08](../negocio/requisitos.md#nfr-08) (RTO ≤ 1h)  
**Região:** `sa-east-1` (São Paulo) — conformidade LGPD e menor latência para usuários brasileiros  
**Método:** IaC declarativa em Terraform + análise Infracost

---

## Metodologia

A estimativa é gerada diretamente do código Terraform em [`/terraform`](https://github.com/gsperim/account-engine-lab/blob/main/terraform/README.md):

```bash
cd terraform
infracost breakdown --config-file infracost.yml --format table
infracost breakdown --config-file infracost.yml --format html --out-file infracost-output.html
```

Os números abaixo são estimativas baseadas em preços públicos AWS de 2026 para `sa-east-1`. Execute `infracost breakdown` para o valor exato no momento do provisionamento.

### Dimensionamento vs. Custo

!!! info "50 req/s é pico, não taxa constante"
    O NFR-02 especifica **capacidade de pico** — o sistema deve suportar 50 req/s quando necessário. Para fins de **dimensionamento de infraestrutura** (nós EKS, conexões RDS, slots Redis), usa-se o pico como referência de capacidade.

    Para fins de **estimativa de custo**, o relevante é a taxa média ao longo do mês. Um sistema de fluxo de caixa de um comerciante não opera a 50 req/s durante 24h/7 dias. Perfil realista:

    | Período | Tráfego estimado | Horas/dia |
    |---------|-----------------|-----------|
    | Horário comercial com picos | ~25 req/s (média) | 4h |
    | Horário comercial normal | ~10 req/s | 8h |
    | Fora do horário comercial | ~2 req/s | 12h |
    | **Média ponderada** | **~9,5 req/s ≈ 10 req/s** | 24h |

    10 req/s médios × 86.400 s/dia × 30 dias = **~26M req/mês** (vs. 130M se fosse constante).

---

## Breakdown por Camada

### Borda

| Recurso | Especificação | Custo/mês |
|---------|---------------|-----------|
| CloudFront | ~26M req/mês (média ~10 req/s) + ~20 GB data out | ~$ 55 |
| WAF v2 | WebACL + 3 rule groups + $0,60/M req | ~$ 24 |
| ACM Wildcard | Gratuito com serviços AWS | $ 0 |
| Route 53 | Hosted zone + queries | ~$ 1 |
| **Subtotal** | | **~$ 80** |

> CloudFront caches GET /consolidacao na edge (~30-40% hit rate estimado), reduzindo chamadas ao API Gateway. WAF cobre OWASP Top 10, SQLi, Known Bad Inputs e rate limit por IP. Custo calculado com taxa média de ~10 req/s — a infraestrutura suporta os picos de 50 req/s sem custo adicional fixo (CloudFront e WAF cobram por requisição processada).

### Compute (EKS)

| Recurso | Especificação | Custo/mês |
|---------|---------------|-----------|
| EKS cluster management | Tarifa fixa | ~$ 73 |
| EC2 t3.medium × 2 (system nodes On-Demand) | 730 h/mês × 2 | ~$ 68 |
| Karpenter Spot nodes (40% utilização estimada) | Varia por workload | ~$ 20 |
| NAT Gateway × 3 | Tarifa por AZ + dados | ~$ 99 |
| EBS gp3 (nós + RabbitMQ PVs) | ~80 GB total | ~$ 8 |
| **Subtotal** | | **~$ 268** |

> Karpenter provisiona nós On-Demand ou Spot conforme a classe de workload. Pods críticos ficam em On-Demand; consolidação e outbox relay elegíveis para Spot (60-70% de economia nesses workloads).

### API Gateway HTTP API

| Recurso | Especificação | Custo/mês |
|---------|---------------|-----------|
| API Gateway HTTP API | ~18M req/mês (26M × 70% miss rate no CloudFront) | ~$ 18 |
| **Subtotal** | | **~$ 18** |

### Banco de Dados

| Recurso | Especificação | Custo/mês |
|---------|---------------|-----------|
| RDS PostgreSQL Multi-AZ × 2 | db.t3.medium, 20 GB gp3 | ~$ 295 |
| RDS Proxy × 2 | $0,015/vCPU-h × 2 vCPU × 730h × 2 proxies | ~$ 44 |
| RDS Backup storage | Snapshots incrementais, 35 dias | ~$ 10 |
| **Subtotal** | | **~$ 349** |

> RDS Proxy resolve o problema de connection pooling — sem ele, 20 pods × 5 conexões = 100 conn, exatamente no limite do `db.t3.medium`.

### Cache

| Recurso | Especificação | Custo/mês |
|---------|---------------|-----------|
| ElastiCache Redis × 2 | cache.t3.micro primary + replica, TLS, KMS | ~$ 36 |
| **Subtotal** | | **~$ 36** |

### Rede (VPC Endpoints)

| Recurso | Especificação | Custo/mês |
|---------|---------------|-----------|
| VPC Endpoints interface × 7 | ECR API+DKR, Secrets Manager, CloudWatch Logs, SSM×3 | ~$ 153 |
| VPC Endpoint gateway S3 | Gratuito | $ 0 |
| **Subtotal** | | **~$ 153** |

> O benefício primário é segurança — tráfego EKS → ECR, Secrets Manager e SSM permanece na rede privada AWS sem passar pelo NAT Gateway.

### Segurança e Detecção

| Recurso | Especificação | Custo/mês |
|---------|---------------|-----------|
| GuardDuty | EKS audit logs + VPC Flow Logs analysis | ~$ 10 |
| CloudTrail | Multi-region trail + S3 + CloudWatch | ~$ 3 |
| VPC Flow Logs | CloudWatch Logs 30 dias | ~$ 7 |
| Security Hub | Findings aggregation + CIS + FSBP | ~$ 2 |
| Inspector v2 | EC2 nodes + ECR images | ~$ 5 |
| KMS CMK | $1/chave/mês + API calls | ~$ 2 |
| Secrets Manager | 2 secrets × $0,40 + API calls | ~$ 2 |
| ECR | Storage ~4 GB + lifecycle | ~$ 1 |
| **Subtotal** | | **~$ 32** |

### Operacional

| Recurso | Especificação | Custo/mês |
|---------|---------------|-----------|
| AWS Backup | Storage 35 dias + cópia cross-region 7 dias (us-east-1) | ~$ 15 |
| CloudWatch Alarms (8) + Dashboard | $0,10/alarme + $3/dashboard | ~$ 4 |
| SNS + Budgets + Cost Anomaly Detection | Notificações e controle financeiro | ~$ 1 |
| **Subtotal** | | **~$ 20** |

---

## Total Estimado

| Camada | Custo/mês |
|--------|-----------|
| Borda (CloudFront + WAF + Route 53) | ~$ 80 |
| Compute (EKS + Karpenter + NAT) | ~$ 268 |
| API Gateway HTTP API | ~$ 18 |
| Banco de dados (RDS + Proxy + Backup) | ~$ 349 |
| Cache (ElastiCache) | ~$ 36 |
| Rede (VPC Endpoints) | ~$ 153 |
| Segurança e Detecção | ~$ 32 |
| Operacional | ~$ 20 |
| **Total baseline** | **~$ 956/mês** |

> **Sensibilidade ao tráfego real:** borda e API Gateway são cobrados por requisição — cresce linearmente com o uso. Se o tráfego médio chegar a 20 req/s, a borda sobe para ~$145 e o API Gateway para ~$35 (total ~$1.030). Compute, RDS, Redis e VPC Endpoints são fixos.

---

## Evolução do Custo por Iteração de Design

| Versão | Total/mês | Decisão principal |
|--------|-----------|-------------------|
| v1 — Amazon MQ CLUSTER + Traefik no EKS | ~$ 1.405 | Ponto de partida (50 req/s constante, MQ oversized) |
| v2 — RabbitMQ self-hosted + API Gateway HTTP API | ~$ 753 | −$ 652 (−46%): MQ eliminado, API GW mais barato |
| v3 — Segurança completa + ops + VPC Endpoints | ~$ 1.243 | +$ 490: camada de produção (custo com 50 req/s constante) |
| v4 — Custo corrigido para tráfego médio realista | ~$ 956 | −$ 287: CloudFront e API GW calculados a ~10 req/s médios |

A diferença v3 → v4 não representa mudança de infraestrutura — apenas corrige a premissa de custo: 50 req/s é o pico que dimensiona a capacidade, mas o custo variável (CloudFront, WAF, API GW) é proporcional ao tráfego médio mensal.

---

## Otimizações Disponíveis

| Otimização | Economia/mês | Quando aplicar |
|------------|-------------|----------------|
| RDS Reserved Instances (1 ano) | ~$ 118 (−40% no RDS) | Após 3 meses com perfil de uso estável |
| ElastiCache Reserved Nodes (1 ano) | ~$ 11 (−30%) | Junto com RDS RI |
| EKS On-Demand system nodes RI (1 ano) | ~$ 20 (−30%) | Junto com RDS RI |
| Remover 3 SSM VPC Endpoints se não usar Session Manager ativamente | ~$ 66 | Se acesso via bastion for preferido |

**Com Reserved Instances (RDS + Redis + EKS):** ~$ 1.094/mês  
**Meta após 1 ano com otimizações completas:** ~$ 1.050–1.100/mês

---

## Como Reproduzir

```bash
cd terraform
infracost auth login          # conta gratuita em infracost.io

# Breakdown por linha
infracost breakdown --config-file infracost.yml --format table

# HTML navegável para apresentação
infracost breakdown --config-file infracost.yml --format html \
  --out-file infracost-output.html

# JSON para CI/CD (Infracost diff em PRs)
infracost breakdown --config-file infracost.yml --format json \
  --out-file infracost-output.json

# Comparar custo entre branches
infracost diff --config-file infracost.yml
```

O `infracost-output.json` armazenado como artefato de CI/CD rastreia a evolução de custo a cada PR que altera infraestrutura — integrável com GitHub Actions, GitLab CI e Azure DevOps.
