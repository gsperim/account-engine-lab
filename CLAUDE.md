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

**Data:** 2026-05-18 | **Branch:** `main` (pós sessão 2026-05-18)
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

### Onde estamos agora — pré-Etapa 9

**Data:** 2026-05-17 | **106+ testes verdes** (lançamentos + consolidado)

#### ✅ Etapa 7 — tudo entregue

| Item | Detalhe |
|------|---------|
| JWT / Spring Security | `oauth2-resource-server` + JWKS Keycloak + `sub` como `operadorId` |
| Outbox cleanup | `OutboxRelay.limparPublicados()` — `@Scheduled(cron="0 0 3 * * *")`, janela 7 dias |
| DLQ consumer | `DlqConsumer` — só métrica + log; sem retry (DLQ é destino final); backoffice futuro documentado |
| Grafana fix | `or vector(0)` no painel Taxa de Erro |
| Idempotência com payload diferente | `payload_hash` (SHA-256) na tabela `lancamentos`; mesma key + payload diferente → 409 `IDEMPOTENCY_KEY_CONFLITO`; replay idempotente retorna existente |
| Estorno de lançamento | `POST /registros/{id}/estorno` — `EstornarLancamentoService`, UUID derivado para idempotência, marca original como estornado |
| `GET /lancamentos/registros/resumo` | Totais de crédito/débito/contagem por data — usado pela reconciliação |
| Reconciliação diária | `ReconciliacaoDiariaJob` — `@Scheduled(cron="0 0 2 * * *")`, compara com `LancamentosGateway`, métrica `saldo_reconciliado_divergencias_total` |
| Recuperação catastrófica | `POST /admin/reconstruir` — `ReconstruirConsolidadoService`, reconstrói saldo iterando dia a dia via gateway |
| `GET /consolidacao/saldo` (período) | Verificado — implementado e coberto em todas as camadas |
| Documentação sincronizada | `stack.md`, `implementacao/index.md`, `dados.md`, `observabilidade/index.md` |
| Hook pre-commit + protocolo de branch | Bloqueia commits diretos em `main`/`develop`; checklist no CLAUDE.md |

#### ✅ Etapa 8 — COMPLETA

| Item | Detalhe |
|------|---------|
| `ci.yml` | Testes + JaCoCo coverage + build Docker + scan Trivy + Infracost (main only) — verde no GitHub Actions |
| `cd-lancamentos.yml` / `cd-consolidado.yml` | CD independente por serviço; SemVer via `VERSION` file; path trigger em main; OIDC sem credenciais estáticas |
| `docs.yml` | MkDocs + C4 (Structurizr) + relatórios de testes + JaCoCo coverage + Infracost → GitHub Pages; trigger workflow_run pós-CI |
| CVEs corrigidos | netty, postgresql via BOM override; `CVE-2026-0861` (libc6) + outros no `.trivyignore`; `cloudfront.tf` syntax fix |
| Chaos Engineering | 5 experimentos executados com Pumba + k6; todos os NFRs confirmados — ver `docs/implementacao/caos.md` |
| Infracost | `$1.219/mês` verificado localmente; terraform init + `--show-skipped`; `INFRACOST_API_KEY` configurado no repo |

#### Pendentes para versões futuras
| Item | Observação |
|------|-----------|
| Backoffice de DLQ | Replay controlado com audit trail — mencionado no `DlqConsumer.java` |
| Idempotência com payload diferente para estorno | Estorno já é idempotente via UUID derivado; conflito de payload não verificado |
| Build info no MDC | `version` + `commit_hash` via `build-info` do Actuator |
| C4 site — estética | Structurizr site-generatr gera HTML sem formatação/CSS — investigar na Etapa 9 |

#### ✅ Entregues nesta sessão (2026-05-18)

| Item | Detalhe |
|------|---------|
| **docs/audit-log** | `dados.md` e `seguranca/index.md` sincronizados com V6 Flyway: schema `audit_log`, retenção 5 anos, mecanismo AFTER_COMMIT+Async |
| **feat/docs-pages** | `docs.yml` expandido: C4 via `structurizr-site-generatr`, relatórios de testes, JaCoCo coverage, Infracost; fallback pages para artefatos ausentes |
| **feat/jacoco** | Plugin `jacoco` em ambos os `build.gradle`; `jacocoTestReport` como `finalizedBy`; excluí `**/generated/**`; artifacts `coverage-{serviço}` no CI |
| **feat/cd-por-servico** | `cd.yml` substituído por `cd-lancamentos.yml` + `cd-consolidado.yml`; SemVer via `VERSION` file; path trigger automático em main |
| **fix/structurizr** | 5 iterações: CLI descontinuado → `structurizr-site-generatr`; permissão Docker (`--user`); `overview.md`; path `build/site/*` |
| **fix/terraform** | `cloudfront.tf`: `override_action { none {} }` expandido para multi-linha (Terraform ≥ 1.8) |
| **fix/infracost** | Path (`cd terraform/`), `terraform init`, CVE-2026-0861 no `.trivyignore`, `--show-skipped` |
| **docs/nav** | Menu reorganizado: Início agrupa Visão Executiva, Planejamento, Glossário e Tags |
| **docs/pipeline** | `pipeline.md` reescrito com implementação real dos 4 workflows |
| **docs/visao-executiva** | Fases 7 e 8 completas: implementação, testes, idempotência, estorno, audit, caos, CD independente |
| **docs/index** | Links para todos os artefatos publicados no GitHub Pages |

#### Próximo passo imediato
**Etapa 9 — Documentação Final e publicação**

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
