# ── Karpenter — Node Autoscaling ─────────────────────────────────────────────
# Karpenter substitui o Cluster Autoscaler com provisioning inteligente de nós:
# provê o tipo de instância exato para o pod (sem desperdício de recurso),
# suporta Spot nativamente e consolida nós ociosos (bin packing).
#
# Arquitetura:
#   - Node group On-Demand (eks.tf): min=2, sempre ativo para pods de sistema
#   - Karpenter: provisiona On-Demand e Spot conforme demanda de pods
#
# O módulo cria: IAM controller role (IRSA), node IAM role, instance profile,
# SQS para interrupção de Spot e EventBridge rules.
# A instalação do Helm chart e NodePool CRDs ficam na Etapa 8.

module "karpenter" {
  source  = "terraform-aws-modules/eks/aws//modules/karpenter"
  version = "~> 20.0"

  cluster_name = module.eks.cluster_name

  # IRSA para o controller Karpenter
  enable_v1_permissions = true

  # Adiciona SSM ao IAM role dos nós provisionados pelo Karpenter
  # — habilita Session Manager sem SSH ou bastion
  node_iam_role_additional_policies = {
    AmazonSSMManagedInstanceCore = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
  }
}

# ── Nota: NodePool e EC2NodeClass (Etapa 8) ───────────────────────────────────
# Após instalar o Helm chart do Karpenter, aplicar os manifests:
#
# NodePool de On-Demand (crítico):
#   spec.template.spec.requirements:
#     - key: karpenter.sh/capacity-type
#       operator: In
#       values: ["on-demand"]
#     - key: node.kubernetes.io/instance-type
#       operator: In
#       values: ["t3.medium", "t3.large", "m5.large"]
#
# NodePool de Spot (não-crítico, consolidação e outbox):
#   spec.template.spec.requirements:
#     - key: karpenter.sh/capacity-type
#       operator: In
#       values: ["spot"]
#   spec.disruption.consolidationPolicy: WhenUnderutilized
#
# EC2NodeClass:
#   spec.amiFamily: AL2
#   spec.instanceProfile: ${module.karpenter.instance_profile_name}
#   spec.subnetSelectorTerms:
#     - tags:
#         karpenter.sh/discovery: ${cluster_name}
#   spec.securityGroupSelectorTerms:
#     - tags:
#         karpenter.sh/discovery: ${cluster_name}
