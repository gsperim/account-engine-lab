# ── EKS ──────────────────────────────────────────────────────────────────────
# Cluster gerenciado com node group de propósito geral.
# Traefik instalado via Helm como Ingress Controller interno (fora deste Terraform).
# HPA configurado por serviço nos manifestos Kubernetes (Etapa 8).

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = local.name_prefix
  cluster_version = var.eks_cluster_version

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # Endpoint restrito por CIDR — apenas IPs autorizados acessam a API K8s
  cluster_endpoint_public_access       = true
  cluster_endpoint_public_access_cidrs = var.eks_admin_cidrs

  # Add-ons gerenciados — atualização automática de patch versions
  cluster_addons = {
    coredns    = { most_recent = true }
    kube-proxy = { most_recent = true }
    vpc-cni    = { most_recent = true }
  }

  eks_managed_node_groups = {
    # Nós On-Demand para pods de sistema (CoreDNS, Karpenter controller, Traefik)
    # Karpenter provisiona nós adicionais (On-Demand e Spot) conforme demanda
    system = {
      instance_types = [var.eks_node_instance_type]

      min_size     = var.eks_node_min
      max_size     = var.eks_node_max
      desired_size = var.eks_node_desired

      disk_size  = 50
      subnet_ids = module.vpc.private_subnets

      # SSM Session Manager — acesso sem SSH, sem bastion, com audit log
      iam_role_additional_policies = {
        AmazonSSMManagedInstanceCore = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
      }

      # IMDSv2 obrigatório — bloqueia SSRF ao endpoint de metadados EC2.
      # hop_limit = 1: pods não acessam o IMDS diretamente (apenas o kubelet no host).
      metadata_options = {
        http_endpoint               = "enabled"
        http_tokens                 = "required"
        http_put_response_hop_limit = 1
      }
    }
  }
}
