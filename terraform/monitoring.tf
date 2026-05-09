# ── SNS — Canal de Alertas ────────────────────────────────────────────────────

resource "aws_sns_topic" "alerts" {
  name              = "${local.name_prefix}-alerts"
  kms_master_key_id = aws_kms_key.main.id
}

resource "aws_sns_topic_subscription" "alerts_email" {
  count     = var.alert_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# ── Alarmes — RDS Lançamentos ─────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "rds_lancamentos_cpu" {
  alarm_name          = "${local.name_prefix}-rds-lancamentos-cpu"
  alarm_description   = "RDS Lançamentos: CPU acima de 80% — risco de degradação"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.lancamentos.identifier
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_lancamentos_connections" {
  alarm_name          = "${local.name_prefix}-rds-lancamentos-connections"
  alarm_description   = "RDS Lançamentos: conexões acima de 80 — próximo do limite do db.t3.medium"
  namespace           = "AWS/RDS"
  metric_name         = "DatabaseConnections"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.lancamentos.identifier
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_lancamentos_storage" {
  alarm_name          = "${local.name_prefix}-rds-lancamentos-storage"
  alarm_description   = "RDS Lançamentos: espaço livre abaixo de 5 GB"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5368709120   # 5 GB em bytes
  comparison_operator = "LessThanThreshold"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.lancamentos.identifier
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
}

# ── Alarmes — RDS Consolidado ─────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "rds_consolidado_cpu" {
  alarm_name          = "${local.name_prefix}-rds-consolidado-cpu"
  alarm_description   = "RDS Consolidado: CPU acima de 80%"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.consolidado.identifier
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
}

# ── Alarmes — ElastiCache ─────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "redis_evictions" {
  alarm_name          = "${local.name_prefix}-redis-evictions"
  alarm_description   = "Redis: evicções em curso — cache cheio, saldos sendo descartados"
  namespace           = "AWS/ElastiCache"
  metric_name         = "Evictions"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 100
  comparison_operator = "GreaterThanThreshold"

  dimensions = {
    ReplicationGroupId = aws_elasticache_replication_group.redis.id
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "redis_cpu" {
  alarm_name          = "${local.name_prefix}-redis-cpu"
  alarm_description   = "Redis: CPU acima de 70% — risco de latência no cache-aside"
  namespace           = "AWS/ElastiCache"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 70
  comparison_operator = "GreaterThanThreshold"

  dimensions = {
    ReplicationGroupId = aws_elasticache_replication_group.redis.id
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
}

# ── Alarmes — API Gateway ─────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "api_gateway_5xx" {
  alarm_name          = "${local.name_prefix}-api-gw-5xx"
  alarm_description   = "API Gateway: taxa de erros 5XX acima de 1%"
  namespace           = "AWS/ApiGateway"
  metric_name         = "5XXError"
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 3
  threshold           = 0.01
  comparison_operator = "GreaterThanThreshold"

  dimensions = {
    ApiId = aws_apigatewayv2_api.main.id
    Stage = aws_apigatewayv2_stage.prod.name
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "api_gateway_latency_p99" {
  alarm_name          = "${local.name_prefix}-api-gw-latency-p99"
  alarm_description   = "API Gateway: latência p99 acima de 2000ms"
  namespace           = "AWS/ApiGateway"
  metric_name         = "IntegrationLatency"
  extended_statistic  = "p99"
  period              = 60
  evaluation_periods  = 5
  threshold           = 2000
  comparison_operator = "GreaterThanThreshold"

  dimensions = {
    ApiId = aws_apigatewayv2_api.main.id
    Stage = aws_apigatewayv2_stage.prod.name
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
}

# ── Alarmes — CloudFront ──────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "cloudfront_5xx" {
  provider            = aws.us_east_1   # métricas CloudFront ficam em us-east-1
  alarm_name          = "${local.name_prefix}-cloudfront-5xx"
  alarm_description   = "CloudFront: taxa de erros 5XX acima de 1%"
  namespace           = "AWS/CloudFront"
  metric_name         = "5xxErrorRate"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanThreshold"

  dimensions = {
    DistributionId = aws_cloudfront_distribution.api.id
    Region         = "Global"
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
}

# ── CloudWatch Dashboard ───────────────────────────────────────────────────────

resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = local.name_prefix

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          title  = "API Gateway — Requisições e Erros"
          period = 60
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiId", aws_apigatewayv2_api.main.id],
            ["AWS/ApiGateway", "5XXError", "ApiId", aws_apigatewayv2_api.main.id],
            ["AWS/ApiGateway", "4XXError", "ApiId", aws_apigatewayv2_api.main.id],
          ]
        }
      },
      {
        type = "metric"
        properties = {
          title  = "RDS — CPU e Conexões"
          period = 60
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", aws_db_instance.lancamentos.identifier],
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", aws_db_instance.consolidado.identifier],
            ["AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", aws_db_instance.lancamentos.identifier],
          ]
        }
      },
      {
        type = "metric"
        properties = {
          title  = "Redis — Evictions e CPU"
          period = 60
          metrics = [
            ["AWS/ElastiCache", "Evictions", "ReplicationGroupId", aws_elasticache_replication_group.redis.id],
            ["AWS/ElastiCache", "CPUUtilization", "ReplicationGroupId", aws_elasticache_replication_group.redis.id],
          ]
        }
      }
    ]
  })
}
