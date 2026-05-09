# ── RDS Proxy — Connection Pooling ────────────────────────────────────────────
# Com EKS escalando pods via HPA, cada pod abre conexões ao RDS.
# db.t3.medium suporta ~100 conexões — com 10 pods × 5 conn/pod já está no limite.
# RDS Proxy mantém um pool de conexões de longa duração ao RDS e multiplexa
# centenas de conexões de pods em poucas conexões reais ao banco.
#
# Pods conectam ao endpoint do Proxy (não ao RDS diretamente).
# DATABASE_URL dos serviços aponta para aws_db_proxy.<nome>.endpoint.

# ── IAM — RDS Proxy acessa Secrets Manager ────────────────────────────────────

resource "aws_iam_role" "rds_proxy" {
  name_prefix = "${local.name_prefix}-rds-proxy-"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "rds.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "rds_proxy" {
  role = aws_iam_role.rds_proxy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = [
          aws_db_instance.lancamentos.master_user_secret[0].secret_arn,
          aws_db_instance.consolidado.master_user_secret[0].secret_arn,
        ]
      },
      {
        Effect   = "Allow"
        Action   = ["kms:Decrypt"]
        Resource = [aws_kms_key.main.arn]
        Condition = {
          StringEquals = {
            "kms:ViaService" = "secretsmanager.${var.aws_region}.amazonaws.com"
          }
        }
      }
    ]
  })
}

# ── Security Group — RDS Proxy ────────────────────────────────────────────────

resource "aws_security_group" "rds_proxy" {
  name_prefix = "${local.name_prefix}-rds-proxy-"
  vpc_id      = module.vpc.vpc_id
  description = "RDS Proxy — aceita pods EKS, encaminha para RDS"

  ingress {
    description     = "PostgreSQL dos pods EKS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Regra separada para evitar dependência circular entre rds.tf e rds_proxy.tf
resource "aws_security_group_rule" "rds_from_proxy" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.rds_proxy.id
  security_group_id        = aws_security_group.rds.id
  description              = "PostgreSQL do RDS Proxy"
}

# ── RDS Proxy — Lançamentos ───────────────────────────────────────────────────

resource "aws_db_proxy" "lancamentos" {
  name                   = "${local.name_prefix}-lancamentos"
  debug_logging          = false
  engine_family          = "POSTGRESQL"
  idle_client_timeout    = 1800
  require_tls            = true
  role_arn               = aws_iam_role.rds_proxy.arn
  vpc_security_group_ids = [aws_security_group.rds_proxy.id]
  vpc_subnet_ids         = module.vpc.private_subnets

  auth {
    auth_scheme = "SECRETS"
    iam_auth    = "DISABLED"
    secret_arn  = aws_db_instance.lancamentos.master_user_secret[0].secret_arn
  }
}

resource "aws_db_proxy_default_target_group" "lancamentos" {
  db_proxy_name = aws_db_proxy.lancamentos.name

  connection_pool_config {
    connection_borrow_timeout    = 120
    max_connections_percent      = 100
    max_idle_connections_percent = 50
  }
}

resource "aws_db_proxy_target" "lancamentos" {
  db_instance_identifier = aws_db_instance.lancamentos.identifier
  db_proxy_name          = aws_db_proxy.lancamentos.name
  target_group_name      = aws_db_proxy_default_target_group.lancamentos.name
}

# ── RDS Proxy — Consolidado ───────────────────────────────────────────────────

resource "aws_db_proxy" "consolidado" {
  name                   = "${local.name_prefix}-consolidado"
  debug_logging          = false
  engine_family          = "POSTGRESQL"
  idle_client_timeout    = 1800
  require_tls            = true
  role_arn               = aws_iam_role.rds_proxy.arn
  vpc_security_group_ids = [aws_security_group.rds_proxy.id]
  vpc_subnet_ids         = module.vpc.private_subnets

  auth {
    auth_scheme = "SECRETS"
    iam_auth    = "DISABLED"
    secret_arn  = aws_db_instance.consolidado.master_user_secret[0].secret_arn
  }
}

resource "aws_db_proxy_default_target_group" "consolidado" {
  db_proxy_name = aws_db_proxy.consolidado.name

  connection_pool_config {
    connection_borrow_timeout    = 120
    max_connections_percent      = 100
    max_idle_connections_percent = 50
  }
}

resource "aws_db_proxy_target" "consolidado" {
  db_instance_identifier = aws_db_instance.consolidado.identifier
  db_proxy_name          = aws_db_proxy.consolidado.name
  target_group_name      = aws_db_proxy_default_target_group.consolidado.name
}
