data "aws_caller_identity" "current" {}

# ── KMS — Customer Managed Key ────────────────────────────────────────────────
# Uma CMK compartilhada com aliases por serviço.
# Key rotation anual automático — substitui chaves da AWS (aws/rds, aws/elasticache)
# para garantir auditoria completa de uso via CloudTrail.

resource "aws_kms_key" "main" {
  description             = "CMK do ${var.project_name} — RDS, ElastiCache, Secrets Manager, logs"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "EnableRootAccess"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }
        Action   = "kms:*"
        Resource = "*"
      },
      {
        Sid    = "AllowCloudWatchLogs"
        Effect = "Allow"
        Principal = {
          Service = "logs.${var.aws_region}.amazonaws.com"
        }
        Action = [
          "kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*",
          "kms:GenerateDataKey*", "kms:DescribeKey"
        ]
        Resource = "*"
      },
      {
        Sid    = "AllowSecretsManager"
        Effect = "Allow"
        Principal = {
          Service = "secretsmanager.amazonaws.com"
        }
        Action = [
          "kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*",
          "kms:GenerateDataKey*", "kms:DescribeKey", "kms:CreateGrant"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_kms_alias" "rds" {
  name          = "alias/${local.name_prefix}/rds"
  target_key_id = aws_kms_key.main.key_id
}

resource "aws_kms_alias" "elasticache" {
  name          = "alias/${local.name_prefix}/elasticache"
  target_key_id = aws_kms_key.main.key_id
}

resource "aws_kms_alias" "secrets" {
  name          = "alias/${local.name_prefix}/secrets"
  target_key_id = aws_kms_key.main.key_id
}

resource "aws_kms_alias" "logs" {
  name          = "alias/${local.name_prefix}/logs"
  target_key_id = aws_kms_key.main.key_id
}
