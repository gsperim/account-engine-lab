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

**Data:** 2026-05-17 | **Branch:** `develop` (pós merge de `feat/structured-logging`)
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

### Onde estamos agora — Etapas 7, 8 e Observabilidade avançada COMPLETAS

**Data:** 2026-05-17 | **106 testes verdes** (lançamentos + consolidado)

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
| `ci.yml` | Testes (lancamentos + consolidado) + build Docker + scan Trivy CRITICAL/HIGH — verde no GitHub Actions |
| `cd.yml` | Push para ECR via OIDC + GitHub Release — trigger manual (`workflow_dispatch`); requer `AWS_DEPLOY_ROLE_ARN` |
| `docs.yml` | MkDocs build + deploy no GitHub Pages via GitHub Actions source — publicado em `https://gsperim.github.io/account-engine-lab/` |
| CVEs corrigidos | netty 4.1.132→4.1.133 (CVE-2026-42583), postgresql 42.7.10→42.7.11 (CVE-2026-42198) via BOM override; `.trivyignore` para CVEs de OS da distroless |
| Chaos Engineering | 5 experimentos executados com Pumba + k6; todos os NFRs confirmados — ver `docs/implementacao/caos.md` |
| Frontend Angular | Plano completo em `docs/implementacao/frontend.md` — implementação após Etapa 8 completa |

#### Pendentes para versões futuras
| Item | Observação |
|------|-----------|
| Backoffice de DLQ | Replay controlado com audit trail — mencionado no `DlqConsumer.java` |
| Idempotência com payload diferente para estorno | Estorno já é idempotente via UUID derivado; conflito de payload não verificado |

#### ✅ Observabilidade avançada — `feat/structured-logging` COMPLETA

| Item | Estado | Detalhe |
|------|--------|---------|
| `LoggingContextFilter` | ✅ | HTTP method + path no MDC para todos os requests, nos dois serviços |
| `MessagingLogContextAspect` | ✅ | Wraps `@RabbitListener` automaticamente — correlation_id + cleanup MDC sem try/finally |
| RabbitMQ observation-enabled | ✅ | traceId propaga pelo RabbitMQ (publisher + consumer); confirmado no Loki |
| Logs estruturados (SLF4J fluent API) | ✅ | `addKeyValue("event", ...)` + `.setCause(t)` em 20 pontos de log nos dois serviços |
| `RestClient.Builder` injetado | ✅ | `LancamentosGatewayAdapter` usa builder auto-configurado → trace propaga em chamadas HTTP |
| Campo `event` no Loki | ✅ | Promtail extrai `event` para structured_metadata |
| Logs Keycloak no Grafana | ✅ | Promtail `output: source: message` restrito a serviços Spring |
| OTEL no outbox-relay | ✅ | `MANAGEMENT_OTLP_TRACING_ENDPOINT` + rede `observability` adicionados |
| `MethodArgumentTypeMismatchException` → 400 | ✅ | `GlobalExceptionHandler` lançamentos retorna 400 para UUID inválido no header |
| Stress test 14.66% falhas | ✅ corrigido | Bug no `stress.js`: token estático do `setup()` expirava aos 5min; teste dura 7min → 401 nos últimos 60s com clock skew 60s do Spring Security. Fix: `expiresAt` no setup + `getCaixaToken/getGestorToken` com renovação per-VU gradual |
| Build info no MDC | 🔲 pendente futuro | `version` + `commit_hash` via `build-info` do Actuator — não bloqueia merge |

#### Próximas etapas
- **Frontend Angular** — plano em `docs/implementacao/frontend.md`
- **Etapa 9** — Documentação Final e publicação

### Stack técnico (referência rápida)
- **Serviços:** Spring Boot 3.5.14 + Java 21, Arquitetura Hexagonal + DDD Tático
- **Mensageria:** RabbitMQ (AMQP) com Outbox Pattern (`@Scheduled` relay + cleanup diário)
- **Persistência:** PostgreSQL (por serviço) + Redis (cache consolidado)
- **Gateway:** Traefik (local) | CloudFront + API Gateway HTTP API (prod)
- **Observabilidade:** Prometheus + Loki + Grafana + Tempo + OTEL Collector
- **Contratos:** OpenAPI 3.1 spec-first, controllers gerados via OpenAPI Generator
- **CI/CD:** GitHub Actions — `ci.yml` (testes + Trivy), `cd.yml` (ECR manual), `docs.yml` (GitHub Pages)

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
