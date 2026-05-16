# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Contexto do Projeto

Desafio técnico para a vaga de **Arquiteto de Soluções no Banco Carrefour**. O problema de negócio é o controle de fluxo de caixa diário de um comerciante, com dois serviços:

1. **Serviço de Lançamentos** — registro de débitos e créditos
2. **Serviço de Consolidado Diário** — relatório com saldo consolidado por dia

NFR crítico: o serviço de lançamentos **não pode ficar indisponível** se o consolidado cair. O consolidado deve suportar **50 req/s** com no máximo **5% de perda** em dias de pico.

## Base de Conhecimento

- [base-de-conhecimento/desafio-arquiteto-solucoes.md](base-de-conhecimento/desafio-arquiteto-solucoes.md) — enunciado completo do desafio com todos os requisitos
- [base-de-conhecimento/roteiro.md](base-de-conhecimento/roteiro.md) — roteiro de execução com etapas, papéis arquiteturais e checklists

## Diretriz Permanente de Documentação

**Toda documentação, ADR, diagrama ou decisão técnica deve identificar o papel arquitetural responsável pela perspectiva.**

Papéis disponíveis:
- 🏛️ Arquiteto Corporativo
- 💼 Arquiteto de Negócios
- 🧩 Arquiteto de Soluções
- ⚙️ Arquiteto de Tecnologia
- 🔒 Arquiteto de Segurança
- 🏗️ Arquiteto de Infraestrutura
- 👁️ Arquiteto de Observabilidade
- 📊 Arquiteto de Dados
- 🛠️ Engenheiro de Software
- 🔐 DevSecOps
- 💻 Desenvolvedor

Cada entrega deve também: justificar o **porquê** da decisão, registrar **trade-offs** considerados, e referenciar o **requisito de negócio ou NFR** que originou a necessidade.

## Idioma

Toda documentação deve ser escrita em **português**, exceto nomenclaturas técnicas (nomes de padrões, ferramentas, protocolos, termos de mercado consolidados como *bounded context*, *event sourcing*, *middleware*, etc.) que permanecem em inglês.

## Estado Atual

**Data:** 2026-05-16 | **Branch:** `main` (entregue) + `arch/etapa-7-implementacao` + `docs/mkdocs-material-features` (pendente de merge)
**Repositório:** https://github.com/gsperim/account-engine-lab

### Etapas concluídas
- ✅ Etapa 1 — Domínio de Negócio
- ✅ Etapa 2 — Arquitetura da Solução (ADR-001 a ADR-005)
- ✅ Etapa 3 — Infraestrutura e Plataforma (ADR-006 a ADR-011 + Terraform)
- ✅ Etapa 4 — Dados e Persistência (ADR-012)
- ✅ Etapa 5 — Segurança
- ✅ Etapa 6 — Observabilidade (stack PLG + Tempo + OTEL + 4 dashboards Grafana)
- 🔄 Etapa 7 — Implementação (JWT ✅; pendentes: Outbox cleanup + DLQ consumer)

### Onde estamos agora — Etapa 7, pendências verificadas no código

Implementação Java completa: 109 arquivos commitados, 71 testes verdes (42 lançamentos + 29 consolidado), k6 smoke/load/stress com auth JWT funcionando (ROPC + Keycloak 26). README reescrito. Repositório publicado.

**Sessão 2026-05-16 — qualidade do portal de documentação (branch `docs/mkdocs-material-features`):**
- MkDocs Material: `navigation.footer`, `navigation.instant` + prefetch + progress, `navigation.tracking`, `navigation.prune`, `toc.follow`, `header.autohide`, `content.code.annotate`, `content.tabs.link`, `content.tooltips`, `content.action.edit` + `repo_url`
- Extensões PyMdown: `mark`, `caret`, `tilde`, `keys`, `tasklist`, `emoji`
- Rastreabilidade em `requisitos.md` reescrita com grid cards + tabs (RF / NFR)
- Removidas referências a avaliadores e ao desafio como audiência (ADR-017, ADR-018, stack.md)
- Jargão inventado removido de 7 arquivos (refletividade, escopo diferencial, degradação graciosa, etc.)

#### 🔴 Funcionalidade incompleta (verificado no código)

| Item | Diagnóstico real |
|------|-----------------|
| **Outbox cleanup job** | `OutboxRelay.java` marca registros como `publicado` mas nunca os deleta. Tabela `outbox` cresce indefinidamente. |
| **DLQ consumer** | Fila `consolidacao.lancamentos.dlq` declarada em `RabbitConfig.java` com DLX configurado, mas sem `@RabbitListener`. Mensagens que falham ficam presas silenciosamente. |

#### 🟡 Fix rápido

| Item | Diagnóstico real |
|------|-----------------|
| **Grafana "No data"** | Painel "Taxa de Erro" retorna vazio quando não há erros — precisa de `or vector(0)` na query PromQL. |

#### 🟢 Diferenciais (não estão nos contratos nem implementados)

| Endpoint | Estado |
|----------|--------|
| `POST /lancamentos/registros/{id}/estorno` | Ausente no `lancamentos.yaml` e no código |
| `GET /lancamentos/registros/resumo` | Ausente no `lancamentos.yaml` e no código |
| `POST /consolidacao/admin/reconstruir` | Ausente no `consolidado.yaml` e no código |

#### Estimativa de esforço restante
- Grafana fix → ~5 min
- Outbox cleanup job → ~30 min (novo `@Scheduled` + migration de delete)
- DLQ consumer → ~45 min (`@RabbitListener` + retry logic)
- Cada diferencial → ~3–4h (contrato OpenAPI + implementação + testes)

### Próximas etapas
- Etapa 8 — Pipeline CI (GitHub Actions) + Chaos Engineering
- Etapa 9 — Documentação Final e publicação

### Stack técnico (referência rápida)
- **Serviços:** Spring Boot 3 + Java 21, Arquitetura Hexagonal + DDD Tático
- **Mensageria:** RabbitMQ (AMQP) com Outbox Pattern (`@Scheduled` relay)
- **Persistência:** PostgreSQL (por serviço) + Redis (cache consolidado)
- **Gateway:** Traefik (local) | CloudFront + API Gateway HTTP API (prod)
- **Observabilidade:** Prometheus + Loki + Grafana + Tempo + OTEL Collector
- **Contratos:** OpenAPI 3.1 spec-first, controllers gerados via OpenAPI Generator

### Protocolo de continuidade entre sessões

Para retomar o trabalho em uma nova sessão, basta dizer:
> "Continue o roteiro a partir do estado atual no CLAUDE.md"

Ao encerrar cada sessão: atualizar este bloco "Estado Atual" e fazer commit.

### Protocolo obrigatório antes de qualquer edit/write

**ANTES de editar ou criar qualquer arquivo**, executar este checklist na ordem:

1. `git rev-parse --abbrev-ref HEAD` — verificar branch atual
2. Se estiver em `main` ou `develop`: **parar** e criar feature branch primeiro
   ```
   git checkout develop
   git checkout -b <tipo>/<descricao-curta>
   ```
3. Só então iniciar os edits

**Tipos de branch:** `feat/`, `fix/`, `docs/`, `refactor/`, `test/`, `chore/`

O hook `pre-commit` também bloqueia commits diretos em `main` e `develop`, mas o checklist evita retrabalho (stash + mover branch) que acontece quando o erro é detectado só no commit.
