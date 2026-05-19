---
tags:
  - arquitetura
  - c4
---

# Diagramas C4

**Perspectiva:** 🧩 Arquiteto de Soluções  
**Níveis:** C4 L1 · L2 · L3 (Components) · L5 (Deployment)  
**Fonte:** [`structurizr/workspace.dsl`](../../structurizr/workspace.dsl) · visualização interativa: `docker compose up structurizr` → http://localhost:8080

---

## C4 L1 — Contexto do Sistema

[![C4 L1 — Contexto do Sistema](assets/contexto.png)](assets/contexto.png)

---

## C4 L2 — Containers do Sistema de Negócio

[![C4 L2 — Containers](assets/containers.png)](assets/containers.png)

[![Legenda](assets/containers-key.png)](assets/containers-key.png)

---

## C4 L2 — Plataforma de Observabilidade

[![C4 L2 — Observabilidade](assets/observabilidade-containers.png)](assets/observabilidade-containers.png)

[![Legenda](assets/observabilidade-containers-key.png)](assets/observabilidade-containers-key.png)

---

## C4 L3 — Components — Serviço de Lançamentos

[![C4 L3 — Lançamentos](assets/lancamentos-components.png)](assets/lancamentos-components.png)

[![Legenda](assets/lancamentos-components-key.png)](assets/lancamentos-components-key.png)

---

## C4 L3 — Components — Outbox Relay

[![C4 L3 — Outbox Relay](assets/outbox-relay-components.png)](assets/outbox-relay-components.png)

---

## C4 L3 — Components — Serviço de Consolidação Diária

[![C4 L3 — Consolidado](assets/consolidado-components.png)](assets/consolidado-components.png)

[![Legenda](assets/consolidado-components-key.png)](assets/consolidado-components-key.png)

---

## C4 L5 — Deployment — Desenvolvimento Local

[![Deployment — Docker Compose](assets/deployment-dev.png)](assets/deployment-dev.png)

---

## C4 L5 — Deployment — Produção AWS

[![Deployment — AWS](assets/deployment-prod.png)](assets/deployment-prod.png)

---

## Fonte canônica

```bash
docker compose up structurizr
# Acesse: http://localhost:8080
# Exporte: menu Diagrams → Export → PNG
# Salve em: docs/arquitetura/assets/
```

O DSL em [`structurizr/workspace.dsl`](../../structurizr/workspace.dsl) é a fonte de verdade. Os PNGs são exportações pontuais — re-exporte sempre que o workspace for atualizado.
