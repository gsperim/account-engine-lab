# ── VPC Endpoints — PrivateLink ──────────────────────────────────────────────
# Sem PrivateLink, todo tráfego EKS → ECR, Secrets Manager, CloudWatch
# passa pelo NAT Gateway ($0,045/GB + custo de API calls).
# Interface endpoints mantêm o tráfego na rede privada da AWS e eliminam
# a exposição à internet para serviços internos.

# ── Security Group para Interface Endpoints ────────────────────────────────────

resource "aws_security_group" "vpc_endpoints" {
  name_prefix = "${local.name_prefix}-vpce-"
  vpc_id      = module.vpc.vpc_id
  description = "Interface VPC Endpoints — HTTPS interno da VPC"

  ingress {
    description = "HTTPS de qualquer recurso na VPC"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  lifecycle {
    create_before_destroy = true
  }
}

# ── S3 — Gateway Endpoint (gratuito) ──────────────────────────────────────────
# Cobre pulls de imagens ECR (armazenadas em S3), artefatos de build e logs.

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = module.vpc.vpc_id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = module.vpc.private_route_table_ids
}

# ── Interface Endpoints ────────────────────────────────────────────────────────
# ECR: pulls de imagem (pode ser GB por deploy — alto impacto no custo NAT)
# Secrets Manager: a cada inicialização de pod, busca credenciais
# CloudWatch Logs: fluxo contínuo de logs dos pods
# SSM*: necessários para Session Manager (acesso sem bastion e sem SSH)

locals {
  interface_endpoints = {
    ecr_api        = "com.amazonaws.${var.aws_region}.ecr.api"
    ecr_dkr        = "com.amazonaws.${var.aws_region}.ecr.dkr"
    secretsmanager = "com.amazonaws.${var.aws_region}.secretsmanager"
    logs           = "com.amazonaws.${var.aws_region}.logs"
    ssm            = "com.amazonaws.${var.aws_region}.ssm"
    ssmmessages    = "com.amazonaws.${var.aws_region}.ssmmessages"
    ec2messages    = "com.amazonaws.${var.aws_region}.ec2messages"
  }
}

resource "aws_vpc_endpoint" "interface" {
  for_each = local.interface_endpoints

  vpc_id              = module.vpc.vpc_id
  service_name        = each.value
  vpc_endpoint_type   = "Interface"
  subnet_ids          = module.vpc.private_subnets
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true
}
