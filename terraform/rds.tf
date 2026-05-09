# ── RDS — Security Group ──────────────────────────────────────────────────────
# Acesso restrito aos nós EKS — sem exposição pública

resource "aws_security_group" "rds" {
  name_prefix = "${local.name_prefix}-rds-"
  vpc_id      = module.vpc.vpc_id
  description = "Acesso ao RDS PostgreSQL apenas a partir dos nós EKS"

  ingress {
    description     = "PostgreSQL from EKS nodes"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }

  lifecycle {
    create_before_destroy = true
  }
}

# ── RDS — Subnet Group ────────────────────────────────────────────────────────

resource "aws_db_subnet_group" "main" {
  name       = local.name_prefix
  subnet_ids = module.vpc.private_subnets

  description = "Subnet group para RDS PostgreSQL — subnets privadas em 3 AZs"
}

# ── RDS — Parameter Group ─────────────────────────────────────────────────────
# shared_buffers e wal_level ajustados para carga transacional

resource "aws_db_parameter_group" "postgres16" {
  name_prefix = "${local.name_prefix}-pg16-"
  family      = "postgres16"
  description = "Parâmetros otimizados para carga transacional"

  parameter {
    name  = "shared_buffers"
    value = "{DBInstanceClassMemory/4096}"  # 25% da RAM
  }

  parameter {
    name  = "wal_level"
    value = "replica"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"  # loga queries > 1s
  }

  lifecycle {
    create_before_destroy = true
  }
}

# ── RDS — Lançamentos ─────────────────────────────────────────────────────────
# Multi-AZ garante o NFR crítico: lançamentos não podem parar por falha de infra.
# manage_master_user_password = true → senha gerenciada pelo Secrets Manager com
# rotação automática. A senha nunca aparece no tfstate nem no tfvars.

resource "aws_db_instance" "lancamentos" {
  identifier = "${local.name_prefix}-lancamentos"

  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  db_name  = "lancamentos"
  username = "lancamentos"

  manage_master_user_password   = true
  master_user_secret_kms_key_id = aws_kms_key.main.key_id

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = aws_kms_key.main.arn

  multi_az               = true
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.postgres16.name

  backup_retention_period   = 7
  backup_window             = "03:00-04:00"
  maintenance_window        = "Sun:04:00-Sun:05:00"
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name_prefix}-lancamentos-final"

  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  performance_insights_kms_key_id       = aws_kms_key.main.arn

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
}

# ── RDS — Consolidado ─────────────────────────────────────────────────────────

resource "aws_db_instance" "consolidado" {
  identifier = "${local.name_prefix}-consolidado"

  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  db_name  = "consolidado"
  username = "consolidado"

  manage_master_user_password   = true
  master_user_secret_kms_key_id = aws_kms_key.main.key_id

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = aws_kms_key.main.arn

  multi_az               = true
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.postgres16.name

  backup_retention_period   = 7
  backup_window             = "03:00-04:00"
  maintenance_window        = "Sun:04:00-Sun:05:00"
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name_prefix}-consolidado-final"

  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  performance_insights_kms_key_id       = aws_kms_key.main.arn

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
}
