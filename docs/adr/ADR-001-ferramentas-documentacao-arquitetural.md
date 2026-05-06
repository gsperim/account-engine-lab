# ADR-001 — Ferramentas de Documentação Arquitetural

**Chapéus:** 🧩 Arquiteto de Soluções · ⚙️ Arquiteto de Tecnologia
**Status:** Aceito
**Data:** 2026-05-06

---

## Contexto

O desafio exige documentação arquitetural em dois níveis distintos:

- **Nível de negócio e solução:** capacidades, processos, serviços e infraestrutura — onde a notação ArchiMate é o padrão de mercado consolidado
- **Nível técnico:** decomposição de sistemas, containers e componentes — onde o C4 Model é mais legível para equipes de desenvolvimento

A escolha das ferramentas impacta a consistência do modelo, a experiência de quem revisa o repositório e a rastreabilidade entre camadas arquiteturais.

**Driver:** D00 — necessidade de documentação arquitetural multi-camada rastreável e acessível no repositório público.

**NFR relacionado:** documentação deve ser versionável, legível e executável localmente sem dependências externas pagas.

---

## Decisão

Adotar uma stack híbrida com três ferramentas:

### 1. Archi — diagramas ArchiMate

Ferramenta open-source de referência para modelagem ArchiMate 3.x.

- Cobre as camadas de Motivação, Negócio, Aplicação e Tecnologia
- Modelo único com múltiplas views — elementos definidos uma vez, reutilizados com consistência
- Arquivo `.archimate` versionado no repositório
- Views exportadas como SVG em `/docs/archimate/` para referência nos documentos markdown

### 2. Structurizr DSL + Structurizr Lite — diagramas C4

Ferramenta de referência do C4 Model (Simon Brown). O DSL é text-based e o Lite roda via Docker.

- DSL versionado em `/docs/structurizr/workspace.dsl`
- Structurizr Lite incluído no `docker-compose.yml` do projeto — quem clonar o repositório acessa os diagramas localmente sem conta externa
- Cobre C4 L1 (System Context), L2 (Containers) e L3 (Components)
- Exportações PNG em `/docs/structurizr/exports/` para referência nos documentos markdown

### 3. Mermaid — diagramas pontuais em markdown

Para diagramas simples embutidos em ADRs, README e documentações de fluxo.

- Renderização nativa no GitHub — zero setup para leitura
- Usado apenas para diagramas de sequência, fluxos e ilustrações pontuais
- Não substitui o modelo ArchiMate nem o Structurizr

---

## Alternativas Consideradas

| Alternativa | Motivo do Descarte |
|-------------|-------------------|
| draw.io para C4 | Diffs ruidosos no Git; Structurizr é mais adequado para C4 pela consistência do modelo |
| Mermaid para C4 completo | Suporte C4 limitado; sem modelo consistente entre views |
| ArchiMate até o nível técnico | Notação menos legível para desenvolvedores nas camadas L2/L3; C4 é mais acessível |
| Structurizr.com (cloud) | Dependência externa paga; Lite resolve o mesmo problema localmente |

---

## Consequências

**Positivas:**
- Cada ferramenta é a referência da sua camada — demonstra conhecimento do ecossistema correto
- Stack totalmente open-source e executável localmente
- Modelo ArchiMate consistente entre todas as views de negócio/solução
- Diagramas C4 versionados como código junto da implementação

**Negativas / Trade-offs:**
- Duas ferramentas para manter em paralelo (Archi + Structurizr)
- Exportações manuais do Archi — não há geração automática de SVG no pipeline CI
- Ponto de transição ArchiMate → C4 na camada de aplicação exige atenção para não criar inconsistências

---

## ADRs Relacionados

Decisões específicas de cada ferramenta foram detalhadas em ADRs dedicados:

- [ADR-002 — MkDocs Material como Portal de Documentação](ADR-002-mkdocs-portal-documentacao.md)
- [ADR-003 — ArchiMate 3.x como Notação Arquitetural](ADR-003-archimate-notacao-arquitetural.md)

---

## Estrutura de Arquivos

```
.
├── archimate/                   # Modelo ArchiMate
│   ├── modelo.archimate
│   └── views/                   # SVGs exportados por view
├── structurizr/                 # Modelo C4
│   ├── workspace.dsl
│   └── exports/                 # PNGs exportados
├── docs/
│   ├── index.md                 # Homepage do portal MkDocs
│   └── adr/                     # Architecture Decision Records
├── base-de-conhecimento/        # Definições e base de conhecimento do projeto
├── mkdocs.yml                   # Configuração do portal de documentação
└── docker-compose.yml
```
