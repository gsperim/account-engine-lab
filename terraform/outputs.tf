output "eks_cluster_name" {
  description = "Nome do cluster EKS — use em: aws eks update-kubeconfig --name <value>"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "Endpoint da API do cluster EKS"
  value       = module.eks.cluster_endpoint
  sensitive   = true
}

output "vpc_id" {
  description = "ID da VPC"
  value       = module.vpc.vpc_id
}

output "api_gateway_execute_url" {
  description = "URL execute-api do API Gateway (usada como origem do CloudFront)"
  value       = "${aws_apigatewayv2_api.main.api_endpoint}/${aws_apigatewayv2_stage.prod.name}"
}

output "cloudfront_domain" {
  description = "Domínio público da API via CloudFront"
  value       = "https://api.${var.domain_name}"
}

output "cloudfront_distribution_id" {
  description = "ID da distribuição CloudFront — necessário para invalidação de cache"
  value       = aws_cloudfront_distribution.api.id
}

output "rds_lancamentos_secret_arn" {
  description = "ARN do secret Secrets Manager com credenciais do RDS lançamentos"
  value       = aws_db_instance.lancamentos.master_user_secret[0].secret_arn
  sensitive   = true
}

output "rds_consolidado_secret_arn" {
  description = "ARN do secret Secrets Manager com credenciais do RDS consolidado"
  value       = aws_db_instance.consolidado.master_user_secret[0].secret_arn
  sensitive   = true
}

output "redis_primary_endpoint" {
  description = "Endpoint primário do Redis"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
  sensitive   = true
}

output "kms_key_arn" {
  description = "ARN da CMK — necessário para pods com acesso ao Secrets Manager via IRSA"
  value       = aws_kms_key.main.arn
}

output "mtls_truststore_bucket" {
  description = "Bucket S3 do truststore mTLS"
  value       = aws_s3_bucket.mtls_truststore.bucket
}

output "rabbitmq_secret_arn" {
  description = "ARN do secret Secrets Manager com credenciais do RabbitMQ"
  value       = aws_secretsmanager_secret.rabbitmq.arn
  sensitive   = true
}

output "rabbitmq_internal_service" {
  description = "Endereço interno do RabbitMQ self-hosted no EKS (definido na Etapa 8)"
  value       = "rabbitmq.messaging.svc.cluster.local:5672"
}

output "security_hub_arn" {
  description = "ARN do Security Hub para integração com ferramentas de SIEM externas"
  value       = "arn:aws:securityhub:${var.aws_region}:${data.aws_caller_identity.current.account_id}:hub/default"
}

output "rds_proxy_lancamentos_endpoint" {
  description = "Endpoint do RDS Proxy de lançamentos — use em DATABASE_URL dos pods"
  value       = aws_db_proxy.lancamentos.endpoint
  sensitive   = true
}

output "rds_proxy_consolidado_endpoint" {
  description = "Endpoint do RDS Proxy de consolidado — use em DATABASE_URL dos pods"
  value       = aws_db_proxy.consolidado.endpoint
  sensitive   = true
}

output "ecr_lancamentos_url" {
  description = "URL do repositório ECR de lançamentos — use em image: do Kubernetes"
  value       = aws_ecr_repository.lancamentos.repository_url
}

output "ecr_consolidado_url" {
  description = "URL do repositório ECR de consolidado"
  value       = aws_ecr_repository.consolidado.repository_url
}

output "karpenter_role_arn" {
  description = "ARN do IRSA role do Karpenter — necessário no values.yaml do Helm chart"
  value       = module.karpenter.iam_role_arn
}

output "karpenter_queue_name" {
  description = "Nome da SQS queue de interrupção do Karpenter"
  value       = module.karpenter.queue_name
}

output "alerts_sns_arn" {
  description = "ARN do tópico SNS de alertas — pode ser usado para integrar com PagerDuty, Slack, etc."
  value       = aws_sns_topic.alerts.arn
}
