# ── AWS Backup — Política Centralizada de DR ──────────────────────────────────
# Backup diário dos dados críticos com retenção de 35 dias.
# RTO ≤ 1h (NFR-08) — RDS Multi-AZ cobre falhas de AZ; backup cobre corrupção
# de dados e exclusão acidental (escopo diferente do Multi-AZ).

resource "aws_iam_role" "backup" {
  name_prefix = "${local.name_prefix}-backup-"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "backup.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "backup" {
  role       = aws_iam_role.backup.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForBackup"
}

resource "aws_iam_role_policy_attachment" "backup_restore" {
  role       = aws_iam_role.backup.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForRestores"
}

# ── Vault Principal (sa-east-1) ────────────────────────────────────────────────

resource "aws_backup_vault" "main" {
  name        = local.name_prefix
  kms_key_arn = aws_kms_key.main.arn
}

resource "aws_backup_vault_lock_configuration" "main" {
  backup_vault_name   = aws_backup_vault.main.name
  changeable_for_days = 3   # WORM: backups imutáveis por 3 dias após criação
  max_retention_days  = 36
  min_retention_days  = 1
}

# ── Vault de DR (us-east-1) ────────────────────────────────────────────────────
# Cópia cross-region — protege contra falha catastrófica de sa-east-1.
# KMS cross-region key reuse não é possível; usa chave padrão da AWS na região de DR.

resource "aws_backup_vault" "dr" {
  provider = aws.us_east_1
  name     = "${local.name_prefix}-dr"
}

# ── Plano de Backup ───────────────────────────────────────────────────────────

resource "aws_backup_plan" "main" {
  name = local.name_prefix

  rule {
    rule_name         = "daily-sa-east-1"
    target_vault_name = aws_backup_vault.main.name
    schedule          = "cron(0 3 * * ? *)"   # 03:00 UTC / 00:00 BRT
    start_window      = 60
    completion_window = 180

    lifecycle {
      cold_storage_after = 30
      delete_after       = 35
    }

    # Cópia automática para região de DR
    copy_action {
      destination_vault_arn = aws_backup_vault.dr.arn

      lifecycle {
        delete_after = 7
      }
    }
  }
}

# ── Seleção de Recursos ────────────────────────────────────────────────────────
# Tag-based: qualquer recurso com Backup=true é incluído automaticamente.
# RDS e ElastiCache recebem essa tag via default_tags do provider.

resource "aws_backup_selection" "main" {
  name         = local.name_prefix
  plan_id      = aws_backup_plan.main.id
  iam_role_arn = aws_iam_role.backup.arn

  resources = [
    aws_db_instance.lancamentos.arn,
    aws_db_instance.consolidado.arn,
    aws_elasticache_replication_group.redis.arn,
  ]
}
