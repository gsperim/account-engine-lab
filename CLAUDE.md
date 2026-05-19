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

**Data:** 2026-05-19 | **Branch:** `main` (pós sessão 2026-05-19)
**Repositório:** https://github.com/gsperim/account-engine-lab

### Etapas concluídas
- ✅ Etapa 1 — Domínio de Negócio
- ✅ Etapa 2 — Arquitetura da Solução (ADR-001 a ADR-005)
- ✅ Etapa 3 — Infraestrutura e Plataforma (ADR-006 a ADR-011 + Terraform)
- ✅ Etapa 4 — Dados e Persistência (ADR-012)
- ✅ Etapa 5 — Segurança
- ✅ Etapa 6 — Observabilidade (stack PLG + Tempo + OTEL + 4 dashboards Grafana)
- ✅ Etapa 7 — Implementação (completa)
- ✅ Etapa 8 — CI/CD + Chaos Engineering (completa)
- ✅ Observabilidade avançada — logs estruturados + tracing end-to-end (completa)
- ✅ Conformidade normativa — ISO 8601/20022/4217/27001/22301/31000/37301 + OWASP ASVS (completa)
- ✅ Audit log assíncrono — `audit_log` pós-commit em lançamentos (completa)
- ✅ Etapa 9 — Documentação Final e fechamento (completa)

### ✅ Entregues nesta sessão (2026-05-19)

| Item | Detalhe |
|------|---------|
| **fix/stress-test-keycloak** | `accessTokenLifespan` 300s → 600s; `getTokenFull()` em auth.js; `setup()` do stress.js calcula `expiresAt` a partir do `expires_in` real — sem thundering herd mid-test |
| **dashboard negócio** | UID corrigido (`negocio`); `$__range` → `${__range_s}s` (Grafana 11 não substitui em instant queries); label mismatch corrigido com `sum()` em Saldo Líquido e Taxa de Estorno |
| **k6 estornos** | ~15% das iterações de lançamento fazem estorno do ID recém-criado; contadores `estornos_ok`/`estornos_falha` adicionados |
| **docs/visao-executiva** | Fase 9 reescrita sem meta-comentário de IA; parágrafo de G-01 removido; referências a gaps removidas |
| **DlqConsumer removido** | Consumer de DLQ eliminado — ACK removia mensagens antes de análise; monitoramento via métricas nativas RabbitMQ |

### Pendentes para versões futuras
| Item | Observação |
|------|-----------|
| Backoffice de DLQ | Replay controlado com audit trail |
| Build info no MDC | `version` + `commit_hash` via `build-info` do Actuator |
| C4 site — estética | Structurizr site-generatr gera HTML sem formatação/CSS |

### Stack técnico (referência rápida)
- **Serviços:** Spring Boot 3.5.14 + Java 21, Arquitetura Hexagonal + DDD Tático
- **Mensageria:** RabbitMQ (AMQP) com Outbox Pattern (`@Scheduled` relay + cleanup diário)
- **Persistência:** PostgreSQL (por serviço) + Redis (cache consolidado)
- **Gateway:** Traefik (local) | CloudFront + API Gateway HTTP API (prod)
- **Observabilidade:** Prometheus + Loki + Grafana + Tempo + OTEL Collector
- **Contratos:** OpenAPI 3.1 spec-first, controllers gerados via OpenAPI Generator
- **CI/CD:** GitHub Actions — `ci.yml` (testes + JaCoCo + Trivy + Infracost), `cd-lancamentos.yml` + `cd-consolidado.yml` (ECR via OIDC, SemVer independente, path trigger), `docs.yml` (GitHub Pages: MkDocs + C4 + testes + cobertura + Infracost)

### Protocolo de continuidade entre sessões

Para retomar o trabalho em uma nova sessão, basta dizer:
> "Continue o roteiro a partir do estado atual no CLAUDE.md"

Ao encerrar cada sessão: atualizar este bloco "Estado Atual" e fazer commit.

### Protocolo obrigatório a cada merge em develop

**A cada `git merge --no-ff` em develop**, antes de fechar a branch:

1. Atualizar o bloco "Estado Atual" do CLAUDE.md — marcar o que foi entregue, remover o que não é mais pendente
2. Atualizar `docs/implementacao/index.md` ou qualquer outro doc afetado pela mudança
3. Incluir essas atualizações no commit da feature branch ou em commit imediato após o merge

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
