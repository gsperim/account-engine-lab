---
tags:
  - ferramentas
  - decisao
---

# Ferramentas e Stack

**Papéis:** 🧩 Arquiteto de Soluções · ⚙️ Arquiteto de Tecnologia

Escolhas de ferramentas para documentação, modelagem e operação do projeto, com justificativa.

---

## Infraestrutura de Plataforma

### Keycloak 26 — Identity Provider (OAuth2/OIDC)

Servidor de identidade escolhido como auth service do sistema. Decisão completa em [ADR-014](adr/ADR-014-identity-provider.md).

**Por que Keycloak:** único candidato que executa localmente via Docker sem emuladores ou mocks, suportando todos os fluxos necessários nativamente — Authorization Code + PKCE (Caixa/Gestor) e Client Credentials (PDV). JWKS endpoint nativo alinhado com [ADR-004](adr/ADR-004-jwt-validacao-local.md). Refresh token rotation configurável por cliente, sem código adicional.

**Estratégia de produção:** substituível por AWS Cognito sem alteração no código da aplicação — apenas o JWKS URL muda (variável de ambiente). Essa portabilidade elimina o vendor lock-in do provedor de identidade.

```bash
# Keycloak disponível em http://localhost:8180
# Admin console: http://localhost:8180/admin (admin/admin em dev)
# JWKS: http://localhost:8180/realms/fluxocaixa/protocol/openid-connect/certs
docker-compose up keycloak
```

### Traefik v3 — API Gateway

Escolhido sobre nginx e Kong para este projeto. Decisão completa em [ADR-007](adr/ADR-007-api-gateway.md).

**Por que Traefik:** service discovery nativo via labels no docker-compose (rotas coexistem com o serviço), TLS com cert auto-signed para dev sem configuração, plugin JWT para validação local (alinhado com [ADR-004](adr/ADR-004-jwt-validacao-local.md)), rate limiting como middleware declarativo.

### PostgreSQL 16 — Banco de dados relacional

Ambos os serviços usam PostgreSQL com instâncias separadas (*database per service* — [P-04](negocio/principios.md)).

**Por que PostgreSQL:** modelo de dados transacional com requisito de consistência forte dentro de cada serviço (lançamentos são append-only; consolidação é calculada a partir de eventos). ACID nativo, suporte a `ON CONFLICT DO NOTHING` para idempotência do outbox. Ecossistema maduro, drivers disponíveis em qualquer linguagem.

**Por que não MongoDB/DynamoDB:** os dados são relacionais e estruturados; não há requisito de schema flexível ou escala de write que justifique um banco não relacional.

### Redis 7 — Cache de alta performance

Cache de saldos consolidados no Serviço de Consolidação (padrão cache-aside).

**Por que Redis:** latência sub-milissegundo para reads; estrutura de dados key-value ideal para `saldo:{data}` → `{total_creditos, total_debitos}`; AOF para durabilidade do cache; `maxmemory-policy allkeys-lru` para gestão automática de memória.

### RabbitMQ 3.13 — Message Broker

Decisão completa em [ADR-002](adr/ADR-002-message-broker.md). Escolhido sobre Kafka por volume moderado e semântica de fila com DLQ.

### Docker + docker-compose — Container runtime

Decisão completa em [ADR-006](adr/ADR-006-container-runtime.md). docker-compose para local e CI; Kubernetes para produção.

---

## Serviços de Aplicação *(a definir na Etapa 7)*

| Componente | Decisão pendente |
|------------|-----------------|
| Linguagem — Serviço de Lançamentos | — |
| Linguagem — Serviço de Consolidação | — |
| Framework HTTP | — |
| ORM / query builder | — |
| Framework de testes | — |

---

---

## Documentação Arquitetural

### ArchiMate 3.x — linguagem de modelagem (conceitos utilizados integralmente)

Os **conceitos** do ArchiMate são aplicados em toda a documentação: Motivation View (Drivers, Stakeholders, Princípios), Business Layer (Value Stream, Capability Map, Bounded Contexts), Application Layer (serviços e contratos) e Technology Layer (topologia e infraestrutura).

**O que está fora do escopo é o [Archi](https://www.archimatetool.com/)** — a ferramenta open-source que implementa a notação gráfica formal do ArchiMate. Em um engajamento real, o Archi produziria um modelo único com múltiplas views e rastreabilidade entre elementos em formato `.archimate`.

Os diagramas são produzidos em Mermaid, eliminando dependência de ferramenta externa e mantendo tudo versionável em texto. A ausência da notação gráfica formal do Archi não afeta as decisões arquiteturais documentadas.

### C4 Model — decomposição técnica

Ferramenta: **[Structurizr DSL + Structurizr Lite](https://structurizr.com/)** (via Docker)

Adotado para os níveis L1 (System Context), L2 (Containers) e L3 (Components). DSL text-based versionado junto ao código. Structurizr Lite disponível via `docker-compose` — sem dependência externa para visualizar os diagramas.

Workspace em `structurizr/workspace.dsl`. Exportações em `structurizr/exports/`.

**Por que não draw.io para C4:** diffs ruidosos no Git; Structurizr é a ferramenta de referência do C4 (Simon Brown) e o DSL é versionável como código.

### Mermaid — diagramas em markdown

Renderização nativa no GitHub e no MkDocs Material. Cobre todos os diagramas do projeto: flowcharts (Value Stream, fluxos de processo), sequence diagrams (contratos de integração), mindmaps e diagramas pontuais nos ADRs. Complementa o Structurizr para os níveis C4 onde a fidelidade visual do DSL não é necessária.

---

## Portal de Documentação

**[MkDocs Material](https://squidfunk.github.io/mkdocs-material/)** — portal navegável servindo os arquivos markdown do repositório.

- Suporte nativo a Mermaid — diagramas nos ADRs e guias renderizam sem configuração adicional
- Busca full-text em português
- Disponível via `docker-compose` na porta `8000`

```bash
# Localmente com venv
.venv/bin/mkdocs serve

# Via Docker
docker-compose up docs
```

---

## O que usamos do TOGAF

Referência metodológica parcial — usamos os conceitos do ADM sem implementar o framework completo:

| Fase ADM | Aplicação | Status |
|----------|-----------|--------|
| A — Architecture Vision | Visão Executiva + Drivers + Stakeholders + Requisitos | ✅ |
| B — Business Architecture | Value Stream, Capability Map, Bounded Contexts, Principles Catalog | ✅ |
| C1 — Application Architecture | C4 L1/L2 (Structurizr) + Contratos de Integração (AsyncAPI + REST) | ✅ |
| C2 — Data Architecture | Modelagem de dados por serviço, consistência eventual | ⏳ Etapa 4 |
| D — Technology Architecture | Topologia docker-compose, Stack, Terraform AWS (21 arquivos) | ✅ |
| F — Migration Planning | Arquitetura de Transição + Plano de Entrega Incremental | ✅ |

**Fora do escopo:** Fases G e H (governança e gestão de mudanças), Architecture Contracts, Enterprise Continuum e TOGAF Reference Models.

**Resgatados de forma simplificada:** Principles Catalog, Stakeholder Map e a distinção ABB vs SBB nos ADRs.
