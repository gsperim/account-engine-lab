# Ferramentas e Stack

**Papéis:** 🧩 Arquiteto de Soluções · ⚙️ Arquiteto de Tecnologia

Escolhas de ferramentas para documentação, modelagem e operação do projeto, com justificativa.

---

## Documentação Arquitetural

### ArchiMate 3.x — ferramenta de referência (fora do escopo do desafio)

Em um engajamento real, a ferramenta de modelagem seria o **[Archi](https://www.archimatetool.com/)** (open-source), cobrindo as camadas de Motivação, Negócio, Aplicação e Tecnologia com a notação formal do ArchiMate 3.x — modelo único, múltiplas views, rastreabilidade entre elementos.

**Decisão para o desafio:** os *conceitos* do ArchiMate (Value Stream, Capability Map, Bounded Contexts, Motivation View) são aplicados integralmente na documentação, mas os *diagramas* são produzidos em Mermaid. Isso elimina dependência de ferramenta externa, mantém tudo versionável em texto e não compromete a substância das decisões arquiteturais — que é o que o desafio avalia.

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

| Fase ADM | Aplicação |
|----------|-----------|
| A — Architecture Vision | Visão da solução, escopo e stakeholders |
| B — Business Architecture | ArchiMate Business Layer |
| C — Information Systems | ArchiMate Application Layer + C4 |
| D — Technology Architecture | ArchiMate Technology Layer |
| F — Migration Planning | Arquitetura de Transição *(diferencial)* |

**Fora do escopo:** Fases G e H (governança e gestão de mudanças), Architecture Contracts, Enterprise Continuum e TOGAF Reference Models.

**Resgatados de forma simplificada:** Principles Catalog, Stakeholder Map e a distinção ABB vs SBB nos ADRs.
