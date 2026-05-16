# ── Geral ────────────────────────────────────────────────────────────────────

variable "aws_region" {
  description = "Região AWS onde os recursos serão criados"
  type        = string
  default     = "sa-east-1"
}

variable "project_name" {
  description = "Nome do projeto — usado como prefixo em todos os recursos"
  type        = string
  default     = "fluxo-caixa"
}

variable "environment" {
  description = "Ambiente de implantação (production, staging)"
  type        = string
  default     = "production"

  validation {
    condition     = contains(["production", "staging"], var.environment)
    error_message = "environment deve ser 'production' ou 'staging'."
  }
}

# ── Rede ─────────────────────────────────────────────────────────────────────

variable "vpc_cidr" {
  description = "CIDR block da VPC"
  type        = string
  default     = "10.0.0.0/16"
}

# ── EKS ──────────────────────────────────────────────────────────────────────

variable "eks_cluster_version" {
  description = "Versão do Kubernetes"
  type        = string
  default     = "1.30"
}

variable "eks_node_instance_type" {
  description = "Tipo de instância dos nós EKS"
  type        = string
  default     = "t3.medium"
}

variable "eks_node_min" {
  description = "Número mínimo de nós no node group"
  type        = number
  default     = 2
}

variable "eks_node_max" {
  description = "Número máximo de nós no node group (HPA trigger)"
  type        = number
  default     = 6
}

variable "eks_node_desired" {
  description = "Número desejado de nós no node group"
  type        = number
  default     = 2
}

variable "eks_admin_cidrs" {
  description = "CIDRs autorizados a acessar a API do cluster EKS (kubectl). Restrinja ao IP do seu bastion/VPN."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

# ── RDS ──────────────────────────────────────────────────────────────────────
# Senhas RDS gerenciadas pelo Secrets Manager via manage_master_user_password = true.
# Não é necessário definir variáveis de senha para RDS.

variable "db_instance_class" {
  description = "Classe de instância RDS (ambos os bancos usam a mesma classe)"
  type        = string
  default     = "db.t3.medium"
}

variable "db_allocated_storage" {
  description = "Armazenamento inicial em GB para cada instância RDS"
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Limite máximo de autoscaling de armazenamento RDS em GB"
  type        = number
  default     = 100
}

# ── ElastiCache ───────────────────────────────────────────────────────────────

variable "redis_node_type" {
  description = "Tipo de nó ElastiCache Redis"
  type        = string
  default     = "cache.t3.micro"
}

variable "redis_auth_token" {
  description = "Token de autenticação do Redis (mínimo 16 caracteres) — armazenado no Secrets Manager"
  type        = string
  sensitive   = true
}

# ── RabbitMQ (self-hosted) ────────────────────────────────────────────────────

variable "rabbitmq_username" {
  description = "Usuário administrador do RabbitMQ"
  type        = string
  default     = "fluxo_caixa"
}

variable "rabbitmq_password" {
  description = "Senha do usuário administrador do RabbitMQ — armazenada no Secrets Manager"
  type        = string
  sensitive   = true
}

# ── DNS e Certificados ────────────────────────────────────────────────────────

variable "domain_name" {
  description = "Domínio público do sistema (ex: fluxo-caixa.com.br). Usado para ACM, Route 53 e CloudFront."
  type        = string
}

# ── CloudFront ────────────────────────────────────────────────────────────────

variable "cloudfront_price_class" {
  description = "Classe de preço do CloudFront. PriceClass_200 inclui América do Sul."
  type        = string
  default     = "PriceClass_200"

  validation {
    condition     = contains(["PriceClass_100", "PriceClass_200", "PriceClass_All"], var.cloudfront_price_class)
    error_message = "cloudfront_price_class deve ser PriceClass_100, PriceClass_200 ou PriceClass_All."
  }
}

variable "cloudfront_origin_secret" {
  description = "Header secreto X-Origin-Verify para garantir que o API Gateway só aceita tráfego do CloudFront"
  type        = string
  sensitive   = true
}

# ── API Gateway — JWT ─────────────────────────────────────────────────────────

variable "jwt_issuer" {
  description = "Issuer do token JWT (URL do provedor de identidade, ex: https://auth.example.com)"
  type        = string
}

variable "jwt_audience" {
  description = "Audience esperado no token JWT (client ID da aplicação)"
  type        = string
}

variable "alb_dns_name" {
  description = "DNS do ALB interno criado pelo AWS Load Balancer Controller no EKS. Preencha após o primeiro deploy Kubernetes (Etapa 8)."
  type        = string
  default     = ""
}

# ── mTLS ─────────────────────────────────────────────────────────────────────

variable "mtls_truststore_path" {
  description = "Caminho local para o arquivo PEM da CA interna (truststore do mTLS). Deixe vazio para desabilitar mTLS."
  type        = string
  default     = ""
}

# ── Monitoramento e FinOps ────────────────────────────────────────────────────

variable "alert_email" {
  description = "E-mail para receber alertas CloudWatch e notificações de orçamento. Deixe vazio para desabilitar."
  type        = string
  default     = ""
}

variable "monthly_budget_usd" {
  description = "Orçamento mensal em USD. Alertas em 80% (previsão), 100% e 110% (real)."
  type        = string
  default     = "1200"
}
