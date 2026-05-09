# ── ElastiCache — Security Group ─────────────────────────────────────────────

resource "aws_security_group" "redis" {
  name_prefix = "${local.name_prefix}-redis-"
  vpc_id      = module.vpc.vpc_id
  description = "Acesso ao Redis apenas a partir dos nós EKS"

  ingress {
    description     = "Redis from EKS nodes"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }

  lifecycle {
    create_before_destroy = true
  }
}

# ── ElastiCache — Subnet Group ────────────────────────────────────────────────

resource "aws_elasticache_subnet_group" "main" {
  name       = local.name_prefix
  subnet_ids = module.vpc.private_subnets

  description = "Subnet group para ElastiCache Redis"
}

# ── ElastiCache — Redis Replication Group ─────────────────────────────────────
# Primary + replica automático para HA (NFR-02).
# Cache-aside pattern — saldo consolidado por dia com TTL gerenciado pela aplicação.
# AOF mantido (appendonly yes) para sobreviver a reinicializações, igual ao docker-compose.

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = local.name_prefix
  description          = "Cache de saldos consolidados — cache-aside pattern"

  node_type            = var.redis_node_type
  num_cache_clusters   = 2  # primary + 1 replica
  engine               = "redis"
  engine_version       = "7.1"
  parameter_group_name = "default.redis7"
  port                 = 6379

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.redis.id]

  # Criptografia em repouso com CMK e em trânsito — ADR-007
  at_rest_encryption_enabled = true
  kms_key_id                 = aws_kms_key.main.arn
  transit_encryption_enabled = true
  auth_token                 = var.redis_auth_token

  automatic_failover_enabled = true
  multi_az_enabled           = true

  snapshot_retention_limit = 3
  snapshot_window          = "02:00-03:00"
  maintenance_window       = "sun:03:00-sun:04:00"
}
