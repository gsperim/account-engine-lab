# Pipeline CI/CD

**Perspectiva:** 🔐 DevSecOps · 🏗️ Arquiteto de Infraestrutura  
**Etapa:** 8 — em elaboração

---

!!! note "Etapa 8 — em elaboração"
    Esta seção será preenchida na Etapa 8 do roteiro. O conteúdo cobrirá:

    - Configuração de CI/CD (GitHub Actions)
    - Dockerfiles multi-stage para cada serviço
    - Manifestos Kubernetes (Deployments, Services, HPA, NetworkPolicy)
    - Infracost diff em PRs que alteram infraestrutura Terraform
    - Instruções de execução local completas

---

## Contexto

A infraestrutura de produção está provisionada via Terraform em [`/terraform`](../../terraform/README.md).

O pipeline conectará o código dos serviços (Etapa 7) à infraestrutura AWS (Etapa 3): build das imagens → push para ECR → deploy no EKS via kubectl/Helm.
