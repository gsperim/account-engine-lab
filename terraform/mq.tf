# ── RabbitMQ — Self-hosted no EKS ────────────────────────────────────────────
#
# Decisão: RabbitMQ é implantado via Helm (bitnami/rabbitmq) no próprio cluster
# EKS, eliminando o custo do Amazon MQ (~$789/mês para CLUSTER_MULTI_AZ).
#
# Motivação: o menor tipo de instância disponível no Amazon MQ (mq.m5.large)
# é superdimensionado para 50 req/s — o Terraform não tem como descer abaixo disso.
# Self-hosted usa o compute do EKS já provisionado, sem custo adicional relevante.
#
# Configuração Helm equivalente ao docker-compose (definida na Etapa 8):
#
#   helm install rabbitmq bitnami/rabbitmq \
#     --namespace messaging \
#     --set replicaCount=3 \
#     --set auth.username=fluxo_caixa \
#     --set auth.password=<secret> \
#     --set persistence.size=8Gi \
#     --set podAntiAffinityPreset=hard \
#     --set topologySpreadConstraints[0].topologyKey=topology.kubernetes.io/zone
#
# Resiliência:
#   - 3 réplicas distribuídas em 3 AZs via podAntiAffinity hard
#   - Quórum de 2/3 nós garante disponibilidade mesmo com falha de 1 AZ
#   - EBS gp3 por réplica — dados persistem em caso de pod restart
#   - Outbox Pattern (ADR-003) garante zero perda de mensagens durante failover
#
# Não há recursos Terraform para RabbitMQ self-hosted — o cluster EKS e os
# node groups já estão definidos em eks.tf. Os manifestos Kubernetes e a
# HelmRelease ficam em /k8s (Etapa 8).
