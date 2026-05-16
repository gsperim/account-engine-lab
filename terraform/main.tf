# ── Locals ────────────────────────────────────────────────────────────────────

locals {
  name_prefix = "${var.project_name}-${var.environment}"

  # sa-east-1 tem três zonas de disponibilidade
  azs             = ["${var.aws_region}a", "${var.aws_region}b", "${var.aws_region}c"]
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
}

# ── VPC ───────────────────────────────────────────────────────────────────────
# Módulo oficial AWS — VPC com subnets públicas (ALB/NAT) e privadas (EKS/dados)
# Um NAT Gateway por AZ para HA (single_nat_gateway = false)

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = local.name_prefix
  cidr = var.vpc_cidr

  azs             = local.azs
  private_subnets = local.private_subnets
  public_subnets  = local.public_subnets

  enable_nat_gateway   = true
  single_nat_gateway   = false
  enable_dns_hostnames = true
  enable_dns_support   = true

  # Tags obrigatórias para o EKS descobrir subnets via auto-discovery
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb"                        = 1
    "kubernetes.io/cluster/${local.name_prefix}"             = "shared"
  }

  public_subnet_tags = {
    "kubernetes.io/role/elb"                                 = 1
    "kubernetes.io/cluster/${local.name_prefix}"             = "shared"
  }
}
