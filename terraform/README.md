# Terraform — Infraestrutura de Produção (AWS / sa-east-1)

Provisiona a infraestrutura de produção do **Controle de Fluxo de Caixa Diário** na AWS
com segurança em profundidade, autoscaling inteligente e FinOps.

## Arquitetura de Produção

```
Internet
  └─► Route 53 (api.dominio.com.br)
        └─► CloudFront [WAF v2 + Shield]
              └─► API Gateway HTTP API [JWT + mTLS + throttling 50 req/s]
                    └─► VPC Link → ALB interno (EKS)
                          ├─► lancamentos pods → RDS Proxy → RDS PostgreSQL
                          └─► consolidado pods  → RDS Proxy → RDS PostgreSQL
                                                └─► ElastiCache Redis

Segurança: KMS CMK · Secrets Manager · IMDSv2 · VPC Endpoints · GuardDuty
           CloudTrail · VPC Flow Logs · Security Hub · Inspector v2 · IAM Access Analyzer
Node scaling: Karpenter (On-Demand + Spot) · SSM Session Manager
Operacional: CloudWatch Alarms + Dashboard · SNS · AWS Backup (cross-region)
FinOps: AWS Budgets · Cost Anomaly Detection
```

## Mapeamento docker-compose → AWS

| Serviço Local          | Recurso AWS                                          | Justificativa                                      |
|------------------------|------------------------------------------------------|----------------------------------------------------|
| `traefik` (borda)      | CloudFront + WAF v2 + API Gateway HTTP API           | WAF GA, JWT nativo, throttling distribuído, Shield |
| aplicações             | EKS + Karpenter (On-Demand e Spot)                   | Autoscaling inteligente, 40-60% economia em Spot   |
| `postgres-lancamentos` | RDS Proxy → RDS Multi-AZ (db.t3.medium) + KMS        | Connection pooling, HA, senha via Secrets Manager  |
| `postgres-consolidado` | RDS Proxy → RDS Multi-AZ (db.t3.medium) + KMS        | Isolamento database-per-service                    |
| `redis`                | ElastiCache Redis 7 (cache.t3.micro × 2) + KMS        | Cache-aside, primary + replica, TLS                |
| `rabbitmq`             | RabbitMQ self-hosted EKS (bitnami, 3 pods)            | Zero custo extra, HA por anti-affinity de AZ       |
| imagens Docker         | Amazon ECR (privado, KMS, imutável, scan on push)     | Supply chain security via Inspector v2             |

## Pré-requisitos

```bash
terraform -version    # >= 1.8
aws configure         # credenciais AWS com permissões adequadas
infracost auth login  # estimativa de custo (conta gratuita)
```

## Uso

```bash
cd terraform

# 1. Copiar e preencher variáveis
cp terraform.tfvars.example terraform.tfvars

# 2. Inicializar providers (sa-east-1 + us-east-1 para CloudFront/WAF/Backup DR)
terraform init

# 3. Verificar o plano
terraform plan

# 4. Fase 1 — infraestrutura completa (sem integração ALB)
terraform apply
```

Após o apply — conectar o cluster e completar deploy em duas fases:

```bash
aws eks update-kubeconfig \
  --region sa-east-1 \
  --name $(terraform output -raw eks_cluster_name)

# Após deploy Kubernetes (Etapa 8):
kubectl get ingress -n default \
  -o jsonpath='{.items[0].status.loadBalancer.ingress[0].hostname}'

# Adicionar ao terraform.tfvars → terraform apply (Fase 2)
# alb_dns_name = "internal-XXXX.sa-east-1.elb.amazonaws.com"
```

## mTLS — Autenticação Mútua (opcional)

```bash
# Gerar CA interna
openssl req -x509 -newkey rsa:4096 \
  -keyout ca-key.pem -out ca.pem -days 3650 -nodes

# No terraform.tfvars: mtls_truststore_path = "./ca.pem"
terraform apply

# Emitir cert de cliente para cada integrador
openssl req -newkey rsa:2048 -nodes -keyout client-key.pem -out client.csr
openssl x509 -req -in client.csr -CA ca.pem -CAkey ca-key.pem \
  -CAcreateserial -out client-cert.pem -days 365
```

## Estimativa de Custo (Infracost)

```bash
infracost breakdown --config-file infracost.yml
infracost diff --config-file infracost.yml
```

Consulte [docs/infraestrutura/custos.md](../docs/infraestrutura/custos.md) para análise completa.

## Estrutura de Arquivos

```
terraform/
├── versions.tf          # Provider sa-east-1 + us-east-1 (CloudFront/WAF/Backup DR)
├── variables.tf         # Variáveis com validação e sensitive
├── main.tf              # VPC multi-AZ
├── eks.tf               # EKS + IMDSv2 + CIDR restriction + SSM
├── karpenter.tf         # Karpenter IAM + SQS + interruption handling
├── rds.tf               # RDS PostgreSQL × 2, Multi-AZ, KMS, Secrets Manager
├── rds_proxy.tf         # RDS Proxy para connection pooling (EKS → Proxy → RDS)
├── cache.tf             # ElastiCache Redis + KMS
├── mq.tf                # RabbitMQ self-hosted — Helm chart docs
├── ecr.tf               # ECR privado + lifecycle + Inspector v2
├── acm.tf               # ACM sa-east-1 (API GW) + us-east-1 (CloudFront)
├── kms.tf               # Customer Managed Key com aliases por serviço
├── secrets.tf           # Secrets Manager + S3 truststore mTLS
├── cloudfront.tf        # CloudFront + WAF v2 + Shield + Route 53
├── api_gateway.tf       # HTTP API + JWT + mTLS + VPC Link + rotas
├── vpc_endpoints.tf     # PrivateLink: ECR, Secrets Manager, CloudWatch, SSM, S3
├── detection.tf         # GuardDuty + CloudTrail + VPC Flow Logs + Security Hub
├── backup.tf            # AWS Backup diário + cross-region DR (us-east-1)
├── monitoring.tf        # CloudWatch Alarms + SNS + Dashboard
├── finops.tf            # Budgets + Cost Anomaly + IAM Access Analyzer + SSM prefs
├── outputs.tf           # Endpoints, ARNs, URLs para Etapa 8
├── terraform.tfvars.example
└── infracost.yml
```

## State Remoto (habilitar antes do primeiro apply em produção)

```bash
aws s3api create-bucket \
  --bucket fluxo-caixa-terraform-state \
  --region sa-east-1 \
  --create-bucket-configuration LocationConstraint=sa-east-1

aws s3api put-bucket-versioning \
  --bucket fluxo-caixa-terraform-state \
  --versioning-configuration Status=Enabled

aws dynamodb create-table \
  --table-name fluxo-caixa-terraform-locks \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region sa-east-1

# Descomente o backend "s3" em versions.tf e execute:
terraform init -migrate-state
```
