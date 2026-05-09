# ADR-011 — Excelência Operacional e Confiabilidade

**Status:** Aceito  
**Data:** 2026-05-08  
**Papéis:** 🧩 Arquiteto de Soluções · 🏗️ Arquiteto de Infraestrutura · ⚙️ Arquiteto de Tecnologia  
**Requisito de origem:** [NFR-02](../negocio/requisitos.md#nfr-02) (50 req/s), [NFR-04](../negocio/requisitos.md#nfr-04) (99,9% disponibilidade), [NFR-08](../negocio/requisitos.md#nfr-08) (RTO ≤ 1h)

---

## Contexto

Com a topologia de rede ([ADR-006](ADR-006-container-runtime.md), [ADR-008](ADR-008-cloud-provider.md)) e a segurança ([ADR-010](ADR-010-seguranca.md)) definidas, um conjunto de decisões operacionais complementares foi necessário para tornar a infraestrutura genuinamente produtiva: connection pooling entre pods e banco, registro privado de imagens, autoscaling inteligente de nós, DR formal, monitoramento com thresholds reais e controle financeiro.

---

## Decisão

Implementar sete controles complementares de excelência operacional: RDS Proxy para connection pooling, VPC Endpoints para isolamento de rede, ECR com imagens imutáveis e scanning, Karpenter para autoscaling inteligente com Spot, AWS Backup com cópia cross-region, CloudWatch Alarms com thresholds calibrados e FinOps com detecção de anomalia de custo.

---

## Decisões e Justificativas

### 1. RDS Proxy — Connection Pooling

**Decisão:** inserir `aws_db_proxy` entre os pods EKS e cada instância RDS.

**Problema:** `db.t3.medium` suporta aproximadamente 100 conexões simultâneas. Com o HPA escalando o serviço de Consolidação para múltiplas réplicas, cada pod abre seu próprio pool de conexões — facilmente saturando o limite do banco antes de saturar a CPU.

**Por que Proxy e não aumentar a instância?** Aumentar para `db.m5.large` (com ~1.000 conexões) custa ~$350/mês adicional. O RDS Proxy custa ~$0,015/hora (≈ $11/mês) e resolve o problema na camada correta.

**Configuração:** `max_idle_connections_percent = 50` mantém conexões ociosas em stand-by para absorver bursts; `connection_borrow_timeout = 120s` evita que pods fiquem bloqueados indefinidamente.

### 2. VPC Endpoints (PrivateLink)

**Decisão:** criar interface endpoints para ECR, Secrets Manager, CloudWatch Logs e SSM; gateway endpoint para S3.

**Problema sem PrivateLink:** todo tráfego de pods para serviços AWS (pulls de imagem ECR, leitura de Secrets Manager, envio de logs) sai pelo NAT Gateway — taxa de $0,045/GB, sem garantia de que o tráfego permanece na rede AWS.

**Trade-off:** interface endpoints custam ~$0,01/AZ/hora (~$22/mês cada). Para ECR (GB de imagens por deploy) e CloudWatch (stream contínuo de logs), o custo do endpoint se paga rapidamente. Para Secrets Manager (chamadas pequenas mas frequentes na inicialização de pods), o benefício é predominantemente de segurança, não de custo.

**Gateway endpoint S3** é gratuito — habilitado por padrão para cobrir os layers de imagem ECR armazenados em S3.

### 3. Amazon ECR — Registro Privado

**Decisão:** repositórios privados com `IMMUTABLE` tags, KMS CMK e `scan_on_push = true`.

**Tags imutáveis:** eliminam o risco de uma tag de produção (ex: `v1.2.3`) ser sobreescrita com uma imagem diferente — evita supply chain attack via tag poisoning.

**scan_on_push + Inspector v2:** a cada push, o Inspector analisa CVEs em packages do SO e dependências da linguagem. Findings vão automaticamente para o Security Hub. O feedback é imediato — antes do deploy no cluster.

**Lifecycle policy (máx. 10 imagens):** controle de custo de storage e redução do backlog de scan.

### 4. Karpenter — Node Autoscaling

**Decisão:** Karpenter como autoscaler de nós em substituição ao Cluster Autoscaler tradicional.

| Critério | Cluster Autoscaler | Karpenter |
|---|---|---|
| Provisioning | Escala node groups existentes | Cria nós sob medida para cada pod |
| Spot support | Manual (node group por tipo) | Nativo, fallback automático para On-Demand |
| Bin packing | Não | Sim — consolida pods e remove nós ociosos |
| Integração AWS | Polling | EventBridge (reativo) |

**Configuração prevista na Etapa 8:** dois NodePools — `on-demand` para pods críticos (lancamentos, banco de dados) e `spot` para workloads tolerantes a interrupção (consolidação em horário de baixa, outbox relay). O Karpenter responde a interrupções de Spot via SQS em < 2 minutos, realocando o pod em outro nó.

**Economia estimada:** 40-60% nos custos de compute para pods que rodam em Spot.

### 5. AWS Backup — DR Formal

**Decisão:** política centralizada com cópia cross-region diária (sa-east-1 → us-east-1).

**Por que não apenas confiar no Multi-AZ do RDS?** Multi-AZ cobre falhas de AZ e hardware — RTO < 60s. Não cobre:
- Corrupção lógica de dados (bug de aplicação, query destrutiva)
- Exclusão acidental (humana ou por deploy defeituoso)
- Catástrofe regional (todos os dados em sa-east-1 inacessíveis)

O backup cobre esses cenários com RPO de 24h e RTO ≤ 1h ([NFR-08](../negocio/requisitos.md#nfr-08)) para restore a partir de snapshot.

**WORM lock (3 dias):** impede exclusão acidental do próprio backup por erros de automação. Janela de 3 dias é suficiente para detectar e reverter sem impedir manutenção normal.

**Retenção:** 35 dias em sa-east-1 (cobertura de mês cheio), 7 dias em us-east-1 (DR mínimo sem custo excessivo).

### 6. CloudWatch Alarms + Dashboard — Monitoramento Operacional

**Decisão:** alarmes com thresholds reais para as métricas de maior risco, SNS como canal unificado.

**Thresholds definidos e por quê:**

| Alarme | Threshold | Motivo |
|---|---|---|
| RDS CPU | 80% / 5 min | db.t3.medium satura rápido sob carga de escrita em lote |
| RDS Connections | 80 | Margem de 20% antes do limite de 100 do db.t3.medium |
| RDS Free Storage | 5 GB | Autoscaling de storage tem lag — alerta antecipado evita `STORAGE_FULL` |
| Redis Evictions | 100/5min | Evicções significam saldos sendo descartados — cache miss inesperado |
| API Gateway 5XX | > 1% / 3 periodos | Diferencia ruído de incidente real |
| API Gateway Latency p99 | 2.000ms | SLA implícito de < 2s para respostas de saldo |
| CloudFront 5XX | > 1% / 10 min | Detecta falhas na cadeia CloudFront → API GW antes do NOC |

### 7. FinOps — Budgets, Anomaly Detection e IAM Access Analyzer

**Decisão:** orçamento mensal com alerta em 80%/100%/110% + detecção de anomalia com ML + IAM Access Analyzer.

**Cost Anomaly Detection (ML):** não substitui os budgets — detecta picos dentro do mês antes de atingir o limite. Caso de uso real: um ataque DDoS que passa pelo WAF pode inflar o custo do NAT Gateway em horas, antes que o budget mensal seja atingido. Threshold de $50/dia é calibrado para pegar anomalias reais sem ruído.

**IAM Access Analyzer:** uma linha no Terraform, zero overhead operacional, detecta automaticamente se alguma política IAM concede acesso externo à conta. Compliance com o princípio do menor privilégio sem revisão manual.

**SSM Session Manager:** elimina portas SSH abertas e a necessidade de bastion hosts. Cada sessão é logada no CloudTrail + S3 — audit completo sem infraestrutura adicional.

---

## Trade-offs Aceitos

| Decisão | Trade-off | Mitigação |
|---|---|---|
| RDS Proxy obrigatório | Adiciona ~$11/mês e um hop na cadeia de conectividade (+1-2ms latência) | Custo irrisório vs. risco de saturação de conexões |
| VPC Endpoints seletivos | ~$154/mês para 7 endpoints — não todos os serviços AWS têm endpoint | Priorizado por volume de tráfego e sensibilidade dos dados |
| Karpenter em vez de CA | Mais complexo de configurar (CRDs) — abordado na Etapa 8 | IAM e SQS já provisionados; NodePools são manifestos K8s simples |
| Backup cross-region diário | +~$5/mês de storage em us-east-1 | Aceito — o custo de um restore sem backup cross-region é imensurável |

---

## Consequências

- `terraform/rds_proxy.tf` — RDS Proxy com connection pooling para ambos os bancos
- `terraform/vpc_endpoints.tf` — PrivateLink para 7 serviços AWS + gateway S3
- `terraform/ecr.tf` — ECR privado com imagens imutáveis + Inspector v2
- `terraform/karpenter.tf` — IAM, SQS e EventBridge para Karpenter; NodePools na Etapa 8
- `terraform/backup.tf` — AWS Backup diário + cópia cross-region + WORM lock
- `terraform/monitoring.tf` — 8 alarmes com thresholds definidos + dashboard + SNS
- `terraform/finops.tf` — Budgets (80%/100%/110%) + Anomaly Detection + IAM Access Analyzer + SSM
- Em Etapa 8: configurar IRSA por pod para acesso granular ao Secrets Manager e ECR
