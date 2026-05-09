# ADR-008 — Provedor de Nuvem para Produção: AWS

**Status:** Aceito  
**Data:** 2026-05-08  
**Papéis:** 🏗️ Arquiteto de Infraestrutura · ⚙️ Arquiteto de Tecnologia · 🏛️ Arquiteto Corporativo  
**Requisito de origem:** NFR-02 (50 req/s), NFR-04 (99,9% disponibilidade), NFR-08 (RTO ≤ 1h)

---

## Contexto

Com a infraestrutura local definida em docker-compose (ADR-006) e o ambiente de produção
previsto para Kubernetes (também ADR-006), é necessário escolher o provedor de nuvem
que hospedará o cluster e os serviços gerenciados de produção.

Os critérios de avaliação foram:

1. **Disponibilidade de serviços gerenciados equivalentes** para PostgreSQL e Redis
2. **Suporte a RabbitMQ** — via serviço gerenciado ou self-hosted com boa integração
3. **Região no Brasil** — latência para usuários e conformidade com LGPD
4. **Maturidade do Kubernetes gerenciado** — qualidade da integração e ecosystem
5. **Cobertura de ferramentas IaC** — suporte no Terraform e Infracost

> **Nota sobre RabbitMQ:** a avaliação inicial considerou Amazon MQ como diferencial.
> Após análise de custo/capacidade (mq.m5.large CLUSTER = ~$789/mês para 50 req/s),
> a decisão foi usar RabbitMQ self-hosted no EKS. Isso uniformiza a decisão entre
> provedores — todos os três suportam self-hosted igualmente bem. O diferencial da AWS
> passou a ser a combinação de RDS, ElastiCache, EKS e região sa-east-1. Detalhes em [ADR-011](ADR-011-excelencia-operacional.md).

---

## Decisão

**AWS na região `sa-east-1` (São Paulo).**

---

## Justificativa

### Serviços gerenciados com equivalência direta

| Container (local) | AWS | GCP | Azure |
|---|---|---|---|
| PostgreSQL 16 | RDS Multi-AZ ✅ | Cloud SQL ✅ | Azure Database for PostgreSQL ✅ |
| Redis 7 | ElastiCache Redis ✅ | Memorystore ✅ | Azure Cache for Redis ✅ |
| RabbitMQ 3.13 | Self-hosted no EKS ✅ | Self-hosted no GKE ✅ | Self-hosted no AKS ✅ |
| Kubernetes | EKS ✅ | GKE ✅ | AKS ✅ |

Com RabbitMQ self-hosted, os três provedores são equivalentes nesse ponto. A escolha recai sobre a qualidade dos demais serviços gerenciados e a presença de região no Brasil.

### RDS PostgreSQL — vantagem AWS

RDS PostgreSQL Multi-AZ com `manage_master_user_password` (rotação automática via Secrets Manager) e Performance Insights é o serviço mais maduro entre os três provedores para PostgreSQL transacional. Cloud SQL oferece feature set similar; Azure Database é ligeiramente mais limitado em extensões.

### Região no Brasil — diferencial decisivo

AWS `sa-east-1` (São Paulo) é a única região de hyperscaler com data center físico no Brasil, garantindo:
- Latência < 10 ms para usuários do Sudeste
- Dados armazenados territorialmente no Brasil — conformidade direta com LGPD sem configuração adicional
- GCP `southamerica-east1` e Azure `brazilsouth` estão também em São Paulo, mas com menor densidade de serviços disponíveis

### Adoção no setor varejista brasileiro

AWS é o provedor dominante entre grandes varejistas e bancos brasileiros (Magazine Luiza, Itaú, Ambev). A equipe de operações provavelmente já possui expertise e credenciais AWS, reduzindo o risco e o tempo de onboarding.

### Ecosystem e ferramental

EKS tem a melhor integração com o restante do ecosystem AWS (IAM Roles for Service Accounts, AWS Load Balancer Controller, Karpenter). Karpenter — o autoscaler de nós escolhido (ADR-011) — é um projeto originado na AWS e tem integração nativa com EC2 Spot e On-Demand que não existe no mesmo nível em GKE/AKS.

---

## Alternativas Consideradas

### GCP (`southamerica-east1`)

**Prós:** GKE tem UX superior para Kubernetes; Cloud SQL tem autoscaling de storage mais transparente.  
**Contras:** Karpenter não tem equivalente nativo no GKE (usa Node Auto Provisioner, menos maduro); menor densidade de serviços em sa-east-1; menor adoção no mercado varejista brasileiro.  
**Descartado** principalmente pela ausência de equivalente ao Karpenter e menor adoção de mercado.

### Azure (`brazilsouth`)

**Prós:** Boa integração com Active Directory (relevante se Carrefour usa Microsoft internamente); AKS estável.  
**Contras:** AKS tem menor integração com ferramental de FinOps e segurança que o EKS; Azure Service Bus não é compatível com o protocolo AMQP 0-9-1 do RabbitMQ — self-hosted no AKS seria necessário, mas sem a qualidade de integração EBS/storage do EKS.  
**Descartado** pela menor sinergia entre os serviços gerenciados escolhidos.

### Amazon MQ (serviço gerenciado para RabbitMQ)

**Avaliado e descartado.** O menor tipo de instância disponível — `mq.m5.large` — é superdimensionado para 50 req/s. CLUSTER_MULTI_AZ (3 brokers) custaria ~$789/mês; SINGLE_INSTANCE ~$263/mês sem HA. Self-hosted no EKS com Helm `bitnami/rabbitmq` (3 réplicas, anti-affinity por AZ) oferece a mesma resiliência por ~$3/mês em EBS. Ver [ADR-011](ADR-011-excelencia-operacional.md).

### Multi-cloud

Descartado. Adiciona complexidade operacional sem benefício real no porte do sistema.

---

## Trade-offs Aceitos

| Trade-off | Mitigação |
|-----------|-----------|
| Vendor lock-in em RDS, ElastiCache e serviços AWS | Mitigado pela camada de abstração via variáveis de ambiente — trocar o provider exige alterar credenciais e manifests, não código de aplicação |
| AWS `sa-east-1` tem preços ~20% superiores a `us-east-1` | Aceito por latência e LGPD |
| Self-hosted RabbitMQ requer conhecimento operacional de RabbitMQ | Helm `bitnami/rabbitmq` abstrai grande parte da operação; Outbox Pattern (ADR-003) garante zero perda de mensagens durante failover |

---

## Consequências

- Terraform em `/terraform/` provisiona toda a infra de produção em AWS `sa-east-1`
- RabbitMQ implantado via Helm no EKS (Etapa 8) — sem Amazon MQ
- Manifests Kubernetes (Etapa 8) referenciam endpoints via `terraform output`
- Secrets dos serviços injetados via AWS Secrets Manager + External Secrets Operator no EKS
