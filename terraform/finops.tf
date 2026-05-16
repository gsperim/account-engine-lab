# ── AWS Budgets ───────────────────────────────────────────────────────────────
# Alerta em 80% (previsão), 100% (atual) e 110% (estouro).
# A estimativa de baseline é ~$1.050/mês após todas as otimizações.

resource "aws_budgets_budget" "monthly" {
  name         = "${local.name_prefix}-monthly"
  budget_type  = "COST"
  limit_amount = var.monthly_budget_usd
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = var.alert_email != "" ? [var.alert_email] : []
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = var.alert_email != "" ? [var.alert_email] : []
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 110
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = var.alert_email != "" ? [var.alert_email] : []
  }
}

# ── Cost Anomaly Detection ────────────────────────────────────────────────────
# Detecta picos anômalos de custo usando ML — ex: DDoS inflando o custo de
# NAT Gateway ou WAF antes que o orçamento seja comprometido.

resource "aws_ce_anomaly_monitor" "main" {
  name              = local.name_prefix
  monitor_type      = "DIMENSIONAL"
  monitor_dimension = "SERVICE"
}

resource "aws_ce_anomaly_subscription" "main" {
  name      = local.name_prefix
  frequency = "DAILY"

  monitor_arn_list = [aws_ce_anomaly_monitor.main.arn]

  subscriber {
    type    = "EMAIL"
    address = var.alert_email != "" ? var.alert_email : "ops@${var.domain_name}"
  }

  # Alerta quando impacto absoluto > $50 no dia
  threshold_expression {
    dimension {
      key           = "ANOMALY_TOTAL_IMPACT_ABSOLUTE"
      values        = ["50"]
      match_options = ["GREATER_THAN_OR_EQUAL"]
    }
  }
}

# ── IAM Access Analyzer ───────────────────────────────────────────────────────
# Detecta automaticamente políticas IAM que concedem acesso a principals externos
# (outras contas, internet). Findings → Security Hub.

resource "aws_accessanalyzer_analyzer" "main" {
  analyzer_name = local.name_prefix
  type          = "ACCOUNT"
}

# ── SSM — Systems Manager ─────────────────────────────────────────────────────
# SSM Session Manager permite acesso interativo a nodes EKS sem SSH e sem bastion.
# Audit log completo das sessões no CloudTrail.
# A policy AmazonSSMManagedInstanceCore é adicionada aos nodes via:
#   - eks.tf (node group padrão)
#   - karpenter.tf (nodes provisionados pelo Karpenter)
# Nenhuma porta SSH (22) precisa estar aberta.

resource "aws_ssm_document" "session_preferences" {
  name            = "${local.name_prefix}-session-preferences"
  document_type   = "Session"
  document_format = "JSON"

  content = jsonencode({
    schemaVersion = "1.0"
    description   = "Session Manager preferences — logging de sessões no S3"
    sessionType   = "Standard_Stream"
    inputs = {
      s3BucketName        = aws_s3_bucket.cloudtrail_logs.bucket
      s3KeyPrefix         = "ssm-sessions/"
      s3EncryptionEnabled = true
      cloudWatchLogGroupName      = "/aws/ssm/sessions/${local.name_prefix}"
      cloudWatchEncryptionEnabled = true
    }
  })
}
