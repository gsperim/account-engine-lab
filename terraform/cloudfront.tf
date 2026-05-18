# ── WAF v2 — Web ACL (CLOUDFRONT scope, us-east-1) ────────────────────────────
# WAF não pode ser associado diretamente a HTTP API (v2) — limitação da AWS.
# Solução: CloudFront na borda com WAF CLOUDFRONT scope.
# Managed rules cobrem OWASP Top 10, entradas maliciosas conhecidas e SQLi.

resource "aws_wafv2_web_acl" "main" {
  provider    = aws.us_east_1
  name        = local.name_prefix
  description = "WAF do ${var.project_name} — OWASP Top 10 + rate limiting por IP"
  scope       = "CLOUDFRONT"

  default_action {
    allow {}
  }

  # ── Regras Gerenciadas pela AWS ──────────────────────────────────────────────

  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 10

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "CommonRuleSetMetric"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 20

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "KnownBadInputsMetric"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "AWSManagedRulesSQLiRuleSet"
    priority = 30

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesSQLiRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "SQLiRuleSetMetric"
      sampled_requests_enabled   = true
    }
  }

  # ── Rate Limiting por IP ──────────────────────────────────────────────────────
  # Bloqueia IPs que excedem 300 req / 5 min (~1 req/s sustentado por IP).
  # Complementa o throttling agregado de 50 req/s do API Gateway (NFR-02 vs NFR-07).

  rule {
    name     = "RateLimitPerIP"
    priority = 5

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = 300
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimitPerIPMetric"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${local.name_prefix}-waf"
    sampled_requests_enabled   = true
  }
}

# ── CloudFront — Distribuição ─────────────────────────────────────────────────
# Camada de borda: WAF, Shield, TLS e cache de respostas GET.
# Origem: API Gateway HTTP API execute-api endpoint (sem custom domain intermediário).

resource "aws_cloudfront_distribution" "api" {
  comment         = "${local.name_prefix} — API Gateway edge distribution"
  enabled         = true
  is_ipv6_enabled = true
  aliases         = ["api.${var.domain_name}"]
  web_acl_id      = aws_wafv2_web_acl.main.arn
  price_class     = var.cloudfront_price_class

  origin {
    # Endpoint execute-api do API Gateway — stage "prod"
    domain_name = "${aws_apigatewayv2_api.main.id}.execute-api.${var.aws_region}.amazonaws.com"
    origin_id   = "api-gateway"
    origin_path = "/${aws_apigatewayv2_stage.prod.name}"

    custom_origin_config {
      http_port              = 443
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    custom_header {
      name  = "X-Origin-Verify"
      value = var.cloudfront_origin_secret
    }
  }

  # ── Comportamento POST /lancamentos — nunca cachear ───────────────────────────
  ordered_cache_behavior {
    path_pattern           = "/lancamentos*"
    target_origin_id       = "api-gateway"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD"]

    cache_policy_id          = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad" # CachingDisabled (AWS managed)
    origin_request_policy_id = "b689b0a8-53d0-40ab-baf2-68738e2966ac" # AllViewerExceptHostHeader

    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  # ── Comportamento GET /consolidacao — cache de saldos na edge ────────────────
  # Saldos de dias fechados são imutáveis — TTL alto reduz carga no EKS.
  # A aplicação deve emitir Cache-Control adequado (ex: max-age=300 para dia anterior).
  ordered_cache_behavior {
    path_pattern           = "/consolidacao*"
    target_origin_id       = "api-gateway"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]

    cache_policy_id          = "658327ea-f89d-4fab-a63d-7e88639e58f6" # CachingOptimized (AWS managed)
    origin_request_policy_id = "b689b0a8-53d0-40ab-baf2-68738e2966ac" # AllViewerExceptHostHeader

    min_ttl     = 0
    default_ttl = 60
    max_ttl     = 300
  }

  # ── Comportamento padrão — sem cache ─────────────────────────────────────────
  default_cache_behavior {
    target_origin_id       = "api-gateway"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD"]

    cache_policy_id          = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad" # CachingDisabled
    origin_request_policy_id = "b689b0a8-53d0-40ab-baf2-68738e2966ac" # AllViewerExceptHostHeader

    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.cloudfront.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  logging_config {
    bucket          = "${aws_s3_bucket.cloudtrail_logs.bucket}.s3.amazonaws.com"
    prefix          = "cloudfront/"
    include_cookies = false
  }
}

# ── Route 53 — Aponta para CloudFront ─────────────────────────────────────────

resource "aws_route53_record" "api" {
  name    = "api.${var.domain_name}"
  type    = "A"
  zone_id = aws_route53_zone.main.zone_id

  alias {
    name                   = aws_cloudfront_distribution.api.domain_name
    zone_id                = aws_cloudfront_distribution.api.hosted_zone_id
    evaluate_target_health = false
  }
}

# ── Shield — Proteção DDoS ────────────────────────────────────────────────────
# Shield Standard é gratuito e automático — este resource habilita monitoramento
# explícito da distribuição CloudFront.
# Shield Advanced (opcional, ~$3.000/mês): descomente o resource abaixo.

resource "aws_shield_protection" "cloudfront" {
  name         = "${local.name_prefix}-cloudfront"
  resource_arn = aws_cloudfront_distribution.api.arn
}

# resource "aws_shield_advanced_automatic_response" "cloudfront" {
#   resource_arn = aws_cloudfront_distribution.api.arn
#   action       = "BLOCK"
# }
