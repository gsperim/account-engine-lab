# ── Secrets Manager ───────────────────────────────────────────────────────────
# Credenciais saem do terraform.tfvars e do terraform.tfstate.
# RDS usa integração nativa (manage_master_user_password = true) — rotação automática.
# Redis auth token e credenciais RabbitMQ ficam aqui.

resource "aws_secretsmanager_secret" "redis_auth_token" {
  name                    = "${local.name_prefix}/redis-auth-token"
  description             = "Token de autenticação do ElastiCache Redis"
  kms_key_id              = aws_kms_key.main.key_id
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "redis_auth_token" {
  secret_id     = aws_secretsmanager_secret.redis_auth_token.id
  secret_string = var.redis_auth_token
}

resource "aws_secretsmanager_secret" "rabbitmq" {
  name                    = "${local.name_prefix}/rabbitmq"
  description             = "Credenciais do RabbitMQ self-hosted"
  kms_key_id              = aws_kms_key.main.key_id
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "rabbitmq" {
  secret_id = aws_secretsmanager_secret.rabbitmq.id
  secret_string = jsonencode({
    username = var.rabbitmq_username
    password = var.rabbitmq_password
  })
}

# ── mTLS — Truststore (S3) ────────────────────────────────────────────────────
# O truststore contém o certificado da CA interna que assina os certificados
# de cliente. Apenas requisições com cert de cliente válido passam pelo mTLS.
# Fluxo: gerar CA interna → emitir certs de cliente → subir CA pem aqui.

resource "aws_s3_bucket" "mtls_truststore" {
  bucket = "${local.name_prefix}-mtls-truststore"
}

resource "aws_s3_bucket_versioning" "mtls_truststore" {
  bucket = aws_s3_bucket.mtls_truststore.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "mtls_truststore" {
  bucket = aws_s3_bucket.mtls_truststore.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.main.key_id
    }
  }
}

resource "aws_s3_bucket_public_access_block" "mtls_truststore" {
  bucket                  = aws_s3_bucket.mtls_truststore.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Upload do truststore (PEM com cert da CA interna).
# Execute: openssl req -x509 -newkey rsa:4096 -keyout ca-key.pem -out ca.pem -days 3650 -nodes
# Depois: cp ca.pem <caminho configurado em var.mtls_truststore_path>

resource "aws_s3_object" "truststore" {
  count  = var.mtls_truststore_path != "" ? 1 : 0
  bucket = aws_s3_bucket.mtls_truststore.id
  key    = "truststore.pem"
  source = var.mtls_truststore_path
  etag   = var.mtls_truststore_path != "" ? filemd5(var.mtls_truststore_path) : null
}
