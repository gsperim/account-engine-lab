# ── ECR — Repositórios Privados ───────────────────────────────────────────────
# Imagens imutáveis: a tag não pode ser sobreescrita — garante rastreabilidade
# de qual versão está em produção e previne supply chain attacks.
# KMS CMK: criptografia das imagens com a mesma chave do projeto.
# scan_on_push: Amazon Inspector v2 analisa CVEs a cada push.

resource "aws_ecr_repository" "lancamentos" {
  name                 = "${local.name_prefix}/lancamentos"
  image_tag_mutability = "IMMUTABLE"

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.main.arn
  }

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "consolidado" {
  name                 = "${local.name_prefix}/consolidado"
  image_tag_mutability = "IMMUTABLE"

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.main.arn
  }

  image_scanning_configuration {
    scan_on_push = true
  }
}

# ── ECR — Lifecycle Policies ──────────────────────────────────────────────────
# Mantém apenas as últimas 10 imagens por repositório — evita acúmulo de
# imagens antigas (custo de storage) e limita a superfície de análise do Inspector.

resource "aws_ecr_lifecycle_policy" "lancamentos" {
  repository = aws_ecr_repository.lancamentos.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Manter apenas as últimas 10 imagens"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_ecr_lifecycle_policy" "consolidado" {
  repository = aws_ecr_repository.consolidado.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Manter apenas as últimas 10 imagens"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# ── Inspector v2 — Scanning Contínuo ──────────────────────────────────────────
# ECR: analisa cada imagem pushed em busca de CVEs (OS packages + linguagem)
# EC2: analisa os nodes EKS continuamente (não apenas no push)
# Findings → Security Hub (centralização automática)

resource "aws_inspector2_enabler" "main" {
  account_ids    = [data.aws_caller_identity.current.account_id]
  resource_types = ["ECR", "EC2"]
}
