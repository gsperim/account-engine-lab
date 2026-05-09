# Segurança

**Perspectiva:** 🔒 Arquiteto de Segurança · 🔐 DevSecOps  
**Etapa:** 5 — em elaboração

---

!!! note "Etapa 5 — em elaboração"
    Esta seção será preenchida na Etapa 5 do roteiro. O conteúdo cobrirá:

    - Modelo de autenticação e autorização (identidade, JWT, JWKS)
    - Critérios de segurança para consumo e integração de serviços *(diferencial)*
    - Estratégia de proteção de dados em trânsito e em repouso
    - Mapeamento de superfície de ataque e controles mitigadores

---

## Contexto

A segurança do sistema é endereçada em duas camadas complementares:

**Camada de sistema** (esta seção — Etapa 5):  
Modelo de identidade, autorização por recurso, proteção dos dados do negócio.

**Camada de infraestrutura AWS** (já documentada):  
WAF v2, mTLS, KMS CMK, Secrets Manager, GuardDuty, CloudTrail e Security Hub — ver [ADR-010](../adr/ADR-010-seguranca.md).
