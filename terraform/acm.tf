# ── Route 53 — Hosted Zone ────────────────────────────────────────────────────
# Zona pública gerenciada. Se o domínio já existe em outra conta/zona,
# substitua por data source: data "aws_route53_zone" "main" { name = var.domain_name }

resource "aws_route53_zone" "main" {
  name = var.domain_name

  comment = "Zona pública do ${var.project_name} — gerenciada pelo Terraform"
}

# ── ACM — Certificado TLS ─────────────────────────────────────────────────────
# Certificado wildcard cobre api.<domínio> e qualquer subdomínio futuro.
# Validação DNS é automática via Route 53 — sem intervenção manual.
# Renovação automática: ACM renova 60 dias antes do vencimento.

resource "aws_acm_certificate" "api" {
  domain_name               = "api.${var.domain_name}"
  subject_alternative_names = ["*.${var.domain_name}"]
  validation_method         = "DNS"

  lifecycle {
    # Cria o novo certificado antes de destruir o antigo — zero downtime
    create_before_destroy = true
  }
}

# ── Route 53 — Registros de Validação ─────────────────────────────────────────
# ACM instrui quais registros CNAME criar; Route 53 os resolve automaticamente.

resource "aws_route53_record" "cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.api.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = aws_route53_zone.main.zone_id
}

# ── ACM — Aguardar Validação (sa-east-1) ─────────────────────────────────────

resource "aws_acm_certificate_validation" "api" {
  certificate_arn         = aws_acm_certificate.api.arn
  validation_record_fqdns = [for record in aws_route53_record.cert_validation : record.fqdn]
}

# ── ACM — Certificado CloudFront (us-east-1 obrigatório) ──────────────────────
# CloudFront exige que o certificado ACM esteja em us-east-1 — requisito da AWS.
# Este certificado é usado exclusivamente na distribuição CloudFront.

resource "aws_acm_certificate" "cloudfront" {
  provider                  = aws.us_east_1
  domain_name               = "api.${var.domain_name}"
  subject_alternative_names = ["*.${var.domain_name}"]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "cloudfront_cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.cloudfront.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = aws_route53_zone.main.zone_id
}

resource "aws_acm_certificate_validation" "cloudfront" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.cloudfront.arn
  validation_record_fqdns = [for record in aws_route53_record.cloudfront_cert_validation : record.fqdn]
}
