# Observabilidade e Monitoramento

**Perspectiva:** 👁️ Arquiteto de Observabilidade · 🔐 DevSecOps  
**Etapa:** 6 — em elaboração

---

!!! note "Etapa 6 — em elaboração"
    Esta seção será preenchida na Etapa 6 do roteiro. O conteúdo cobrirá:

    - Definição dos três pilares: logs estruturados, métricas e rastreamento distribuído
    - SLOs e SLAs dos serviços (baseados nos NFRs)
    - Estratégia de alertas e resposta a incidentes
    - Stack de observabilidade *(diferencial)*

---

## Contexto

A observabilidade operacional da infraestrutura AWS (CloudWatch Alarms, GuardDuty, Security Hub, VPC Flow Logs) está definida nos [ADR-010](../adr/ADR-010-seguranca.md) e [ADR-011](../adr/ADR-011-excelencia-operacional.md).

Esta seção abordará a observabilidade do **comportamento de negócio** dos serviços: rastreabilidade de lançamentos, latência de consolidação, taxa de erros por operação e alertas orientados a SLO.
