terraform {
  required_version = ">= 1.8"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Descomente para state remoto em produção (S3 + DynamoDB lock):
  # backend "s3" {
  #   bucket         = "fluxo-caixa-terraform-state"
  #   key            = "production/terraform.tfstate"
  #   region         = "sa-east-1"
  #   dynamodb_table = "fluxo-caixa-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
      Repository  = "github.com/gsperim/desafio-carrefour"
    }
  }
}

# CloudFront e WAF (CLOUDFRONT scope) exigem recursos em us-east-1
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
      Repository  = "github.com/gsperim/desafio-carrefour"
    }
  }
}
