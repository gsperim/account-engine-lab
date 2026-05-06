# ADR-002 — MkDocs Material como Portal de Documentação

**Chapéus:** 🧩 Arquiteto de Soluções · 🔐 DevSecOps
**Status:** Aceito
**Data:** 2026-05-06

---

## Contexto

O repositório acumula múltiplos tipos de artefatos: ADRs, diagramas ArchiMate exportados, diagramas C4, requisitos, roteiro e documentação técnica. Navegar por arquivos markdown diretamente no GitHub fragmenta a experiência de quem avalia ou mantém o projeto.

A documentação é critério explícito de avaliação do desafio. Um portal navegável, com busca e renderização integrada de diagramas, comunica melhor a qualidade arquitetural do que uma árvore de arquivos.

**Driver:** D00 — documentação como artefato de primeira classe, acessível sem fricção.

---

## Decisão

Adotar **MkDocs com o tema Material** como portal de documentação, servido via Docker no `docker-compose` do projeto.

**Razões:**

- Configuração mínima — um `mkdocs.yml` e um `Dockerfile` simples
- MkDocs Material tem suporte nativo a **Mermaid** — diagramas embutidos nos ADRs renderizam sem configuração adicional
- SVGs exportados do Archi e PNGs do Structurizr são referenciados como imagens estáticas nas páginas
- Busca full-text nativa — encontrar qualquer ADR ou decisão sem navegar manualmente
- Serve os mesmos arquivos `.md` já escritos — zero duplicação de conteúdo
- Container leve (imagem base `squidfunk/mkdocs-material`)

**Porta:** `8000` (local)

**Estrutura de navegação (`mkdocs.yml`):**
```yaml
nav:
  - Início: index.md
  - Desafio: base-de-conhecimento/desafio-arquiteto-solucoes.md
  - Roteiro: base-de-conhecimento/roteiro.md
  - Arquitetura:
      - ADRs: docs/adr/
      - ArchiMate: docs/archimate/
      - C4 Model: docs/structurizr/
  - Serviços:
      - Lançamentos: docs/servicos/lancamentos.md
      - Consolidado Diário: docs/servicos/consolidado.md
```

---

## Alternativas Consideradas

| Alternativa | Motivo do Descarte |
|-------------|-------------------|
| GitHub Pages (geração automática) | Exige configuração de CI/CD antes do código estar pronto; MkDocs local é imediato |
| Docusaurus | Overhead de Node.js desnecessário para documentação predominantemente markdown |
| Confluence / Notion | Ferramentas externas — não vivem no repositório |
| Apenas markdown no GitHub | Navegação fragmentada; sem renderização de Mermaid nas versões mais antigas; sem busca |

---

## Consequências

**Positivas:**
- Avaliadores do desafio têm uma entrada única para toda a documentação
- Mermaid renderiza nos ADRs sem esforço extra
- Porta de entrada natural para apresentar a arquitetura em camadas

**Negativas / Trade-offs:**
- O `mkdocs.yml` precisa ser atualizado a cada novo documento adicionado
- Mais um serviço no `docker-compose` (impacto mínimo — container leve)
- SVGs do Archi precisam estar exportados para aparecer no portal — exportação ainda é manual
