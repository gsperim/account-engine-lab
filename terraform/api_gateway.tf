# ── API Gateway HTTP API ──────────────────────────────────────────────────────
# HTTP API (v2) — menor latência e custo que REST API (v1).
# JWT Authorizer nativo substitui o plugin experimental do Traefik em produção
# e implementa a validação local de tokens descrita no ADR-004.
# Consulte ADR-009 para a decisão completa.

resource "aws_apigatewayv2_api" "main" {
  name          = local.name_prefix
  protocol_type = "HTTP"
  description   = "API Gateway — Controle de Fluxo de Caixa Diário"

  cors_configuration {
    allow_headers = ["Content-Type", "Authorization"]
    allow_methods = ["GET", "POST", "OPTIONS"]
    allow_origins = ["https://api.${var.domain_name}"]
    max_age       = 300
  }

  # ── mTLS — Autenticação Mútua ─────────────────────────────────────────────
  # Dupla autenticação: servidor → cliente (TLS normal) + cliente → servidor (cert de cliente).
  # O truststore (PEM da CA interna) fica no S3 e é referenciado aqui.
  # Para emitir certs de cliente: openssl req -newkey rsa:2048 -CA ca.pem -CAkey ca-key.pem ...
  # Habilita somente após upload do truststore (var.mtls_truststore_path != "").

  dynamic "mutual_tls_authentication" {
    for_each = var.mtls_truststore_path != "" ? [1] : []
    content {
      truststore_uri     = "s3://${aws_s3_bucket.mtls_truststore.bucket}/truststore.pem"
      truststore_version = try(aws_s3_object.truststore[0].version_id, null)
    }
  }
}

# ── JWT Authorizer ────────────────────────────────────────────────────────────
# Valida tokens JWT via JWKS público do provedor de identidade.
# A validação ocorre no API Gateway — sem roundtrip ao IdP por requisição (ADR-004).
# jwt_issuer e jwt_audience são configurados no tfvars por ambiente.

resource "aws_apigatewayv2_authorizer" "jwt" {
  api_id           = aws_apigatewayv2_api.main.id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  name             = "jwt-authorizer"

  jwt_configuration {
    audience = [var.jwt_audience]
    issuer   = var.jwt_issuer
  }
}

# ── CloudWatch Logs ───────────────────────────────────────────────────────────

resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/${local.name_prefix}"
  retention_in_days = 30
}

# ── Stage de Produção ─────────────────────────────────────────────────────────
# throttling_rate_limit = 50 → garante NFR-02 (50 req/s agregado no gateway).
# throttling_burst_limit = 100 → absorve picos pontuais sem rejeitar requisições.

resource "aws_apigatewayv2_stage" "prod" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "prod"
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway.arn
  }

  default_route_settings {
    throttling_rate_limit  = 50
    throttling_burst_limit = 100
    logging_level          = "INFO"
  }
}

# ── VPC Link ──────────────────────────────────────────────────────────────────
# Conecta o API Gateway à rede privada do EKS sem expor o ALB interno à internet.
# O tráfego percorre: API Gateway → VPC Link → ALB interno (EKS) → pods.

resource "aws_security_group" "vpc_link" {
  name_prefix = "${local.name_prefix}-vpc-link-"
  vpc_id      = module.vpc.vpc_id
  description = "VPC Link — tráfego do API Gateway para o ALB interno do EKS"

  egress {
    description = "HTTP para o ALB interno do EKS"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_apigatewayv2_vpc_link" "main" {
  name               = local.name_prefix
  subnet_ids         = module.vpc.private_subnets
  security_group_ids = [aws_security_group.vpc_link.id]
}

# ── Integrações ───────────────────────────────────────────────────────────────
# O ALB interno é criado pelo AWS Load Balancer Controller durante o deploy K8s (Etapa 8).
# Preencha var.alb_dns_name com o DNS do ALB após o primeiro deploy do cluster.
# Exemplo: internal-XXXX.sa-east-1.elb.amazonaws.com

resource "aws_apigatewayv2_integration" "lancamentos" {
  api_id             = aws_apigatewayv2_api.main.id
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  integration_uri    = "http://${var.alb_dns_name}/lancamentos/{proxy}"
  connection_type    = "VPC_LINK"
  connection_id      = aws_apigatewayv2_vpc_link.main.id

  request_parameters = {
    "overwrite:path" = "$request.path"
  }
}

resource "aws_apigatewayv2_integration" "consolidado" {
  api_id             = aws_apigatewayv2_api.main.id
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  integration_uri    = "http://${var.alb_dns_name}/consolidacao/{proxy}"
  connection_type    = "VPC_LINK"
  connection_id      = aws_apigatewayv2_vpc_link.main.id

  request_parameters = {
    "overwrite:path" = "$request.path"
  }
}

# ── Rotas — Lançamentos ───────────────────────────────────────────────────────

resource "aws_apigatewayv2_route" "post_lancamentos" {
  api_id             = aws_apigatewayv2_api.main.id
  route_key          = "POST /lancamentos"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
  target             = "integrations/${aws_apigatewayv2_integration.lancamentos.id}"
}

resource "aws_apigatewayv2_route" "get_lancamentos" {
  api_id             = aws_apigatewayv2_api.main.id
  route_key          = "GET /lancamentos/{proxy+}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
  target             = "integrations/${aws_apigatewayv2_integration.lancamentos.id}"
}

# ── Rotas — Consolidação Diária ───────────────────────────────────────────────

resource "aws_apigatewayv2_route" "get_consolidacao" {
  api_id             = aws_apigatewayv2_api.main.id
  route_key          = "GET /consolidacao/{proxy+}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
  target             = "integrations/${aws_apigatewayv2_integration.consolidado.id}"
}

# ── Nota: Domínio e Route 53 ─────────────────────────────────────────────────
# O endpoint público api.<domínio> é gerenciado pelo CloudFront (cloudfront.tf).
# O API Gateway não tem custom domain próprio — o CloudFront aponta diretamente
# para o endpoint execute-api, mantendo a borda de segurança no CloudFront.
