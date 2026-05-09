# Dados e Persistência

**Perspectiva:** 📊 Arquiteto de Dados · 🔗 Arquiteto de Integração  
**Etapa:** 4 — em elaboração  
**Requisito de origem:** ADR-001 (database per service), ADR-003 (Outbox Pattern)

---

!!! note "Etapa 4 — em elaboração"
    Esta seção será preenchida na Etapa 4 do roteiro. O conteúdo cobrirá:

    - Modelagem dos dados por serviço (schemas PostgreSQL para `lancamentos` e `consolidado`)
    - Justificativa dos mecanismos de persistência (ADR)
    - Estratégia de consistência eventual entre os serviços
    - Definição formal dos eventos de domínio e contratos de mensagem (ABB → SBB)

---

## Contexto

O sistema adota **database per service** (ADR-001): cada serviço tem seu próprio banco de dados e nenhum serviço acessa diretamente o banco do outro. A comunicação entre serviços acontece via eventos assíncronos no RabbitMQ, com garantia de entrega pelo Outbox Pattern (ADR-003).

Os contratos de mensagem de alto nível estão em [Contratos de Integração](../engenharia/contratos.md).
