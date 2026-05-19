---
tags:
  - evolucoes
  - backlog
  - arquitetura
---

# Evoluções Futuras e Backlog Técnico

**Papel:** 🧩 Arquiteto de Soluções · 🛠️ Engenheiro de Software  
**Data da análise:** 2026-05-18

Este documento registra o que foi deixado fora do escopo com justificativa técnica. Os gaps técnicos identificados na análise do código foram todos resolvidos — ver [histórico de gaps](#histórico-de-gaps-resolvidos).

---

## Decisões de escopo — deixados para versões futuras

Estes itens foram identificados durante o desenvolvimento e conscientemente adiados. A decisão de escopo em cada caso está justificada abaixo.

---

### 1. Backoffice de reprocessamento da DLQ

**Escopo:** reproduzir manualmente mensagens da Dead-Letter Queue para a fila principal, com trilha de auditoria.

**O que está implementado:**  
`DlqConsumer.java` (`consolidado/adapter/in/messaging/`) escuta a fila `consolidacao.lancamentos.dlq`. Para cada mensagem recebida, incrementa a métrica `dlq_mensagens_total`, extrai o header `x-death` para contar tentativas, e registra em ERROR. Não há replay automático.

```java
// DlqConsumer.java — comportamento atual
@RabbitListener(queues = RabbitConfig.DLQ)
public void consumir(Message message) {
    // incrementa métrica + loga ERROR → sem reprocessamento
}
```

**Por que foi adiado:**  
Replay automático em DLQ mascara falhas sistêmicas — se uma mensagem foi para a DLQ, é porque falhou repetidamente. Reprocessar sem investigar a causa raiz pode acumular saldo incorreto. O caminho correto exige um backoffice com:
- Visualização da mensagem e do histórico de falhas
- Decisão humana (replay ou descarte)
- Registro em audit trail de quem reprocessou, quando, e o resultado

**Caminho de evolução:**  
Endpoint `POST /admin/dlq/{messageId}/replay` com `ROLE_ADMIN`, persistência em `audit_log`, e painel no Grafana mostrando volume na DLQ ao longo do tempo.

---

### 2. Build info no MDC (version + commit hash)

**Escopo:** enriquecer o MDC de logging com a versão do serviço e o hash do commit que gerou a imagem.

**O que está implementado:**  
`LoggingContextFilter.java` (ambos os serviços) adiciona `http_method`, `http_path` (sanitizado) e `correlation_id` ao MDC. `MessagingLogContextAspect.java` adiciona contexto equivalente nos listeners RabbitMQ. Nenhum adiciona `version` ou `commit_hash`.

**Por que foi adiado:**  
O ganho só se materializa quando há múltiplas versões em produção simultaneamente (canary, blue-green). Sem esse cenário, o campo é informacional sem impacto em debugging.

**Caminho de evolução:**  
Adicionar em ambos os `build.gradle`:
```groovy
springBoot {
    buildInfo()
}
```
Expor via `BuildProperties` injetado nos filtros. O campo `git.commit.id.abbrev` vem do plugin `com.gorylenko.gradle-git-properties`.

---

### 3. Estética do site C4 (Structurizr site-generatr)

**Escopo:** o HTML gerado pelo `structurizr-site-generatr` não carrega folha de estilo — diagramas aparecem sem formatação visual.

**O que está implementado:**  
O workflow `docs.yml` executa `ghcr.io/avisi-cloud/structurizr-site-generatr` com o `workspace.dsl`. O output em `build/site/` é copiado para `site/c4/` no GitHub Pages. Os diagramas estão corretos em conteúdo, mas sem CSS.

**Por que foi adiado:**  
O problema é de aparência, não de conteúdo arquitetural. Os diagramas renderizam no Structurizr Lite local (`docker compose up structurizr`) sem nenhuma limitação.

**Caminho de evolução:**  
Injetar CSS customizado via `--custom-stylesheet` (opção da ferramenta, se disponível na versão atual), ou pós-processar o HTML com `sed`/`python` no step do workflow.

---

## Análise do status dos pendentes documentados

Durante o desenvolvimento foram registrados quatro itens como "pendentes para versões futuras" no `CLAUDE.md`. A análise do código confronta esse registro com o que está realmente implementado:

| Item documentado | Status real |
|------------------|-------------|
| Backoffice de DLQ | ✅ Correto — `DlqConsumer` só loga e mede; sem replay. Decisão correta. |
| Idempotência com payload diferente para estorno | ✅ **Resolvido** — `EstornarLancamentoService` deriva o ID do estorno deterministicamente (`UUID.nameUUIDFromBytes("estorno-" + originalId)`). Replay retorna 409 via colisão de PK. |
| Build info no MDC | ✅ Correto — não implementado, filtros de MDC não incluem `version` ou `commit_hash`. |
| C4 site — estética | ✅ Correto — HTML gerado sem CSS pelo `structurizr-site-generatr`. Conteúdo correto; aparência limitada. |

---

## Histórico de gaps resolvidos

Gaps identificados na análise do código (2026-05-18) e resolvidos na mesma sessão.

| Gap | Severidade | Resolução |
|-----|-----------|-----------|
| **G-01** — Consumer do consolidado sem idempotência | Alta | `lancamentos_aplicados` (PK por `lancamento_id`) + verificação em `ProcessarLancamentoService` antes de aplicar crédito/débito. Migration V2. |
| **G-02** — `LancamentosGatewayAdapter` sem resiliência | Média | `@Retry(lancamentos-gateway)` + `@CircuitBreaker(lancamentos-gateway)` com fallback que lança `GatewayException`. Config em `application.properties`. |
| **G-03** — `ReconciliacaoDiariaJob` sem proteção contra execução concorrente | Média | ShedLock via JDBC (`shedlock-provider-jdbc-template`). `@SchedulerLock(name="reconciliacao-diaria", lockAtMostFor="PT1H")`. Migration V3 para tabela `shedlock`. |
| **G-04** — `ReconstruirConsolidadoService` com transação única para o período inteiro | Baixa | `ReconstruirPorDataHelper` (`@Transactional(REQUIRES_NEW)` + `@CacheEvict`) — bean separado para que o Spring AOP aplique a propagação corretamente. Falha em uma data não reverte as anteriores; loop continua com `try/catch` por data. |
| **G-05** — Audit log ausente no consolidado para operações administrativas | Baixa | Infraestrutura de audit portada: `AuditEvento`, `AuditPublisher` (port), `AuditPublisherAdapter`, `AuditEventListener` (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`), `AuditLogJpaEntity`, Migration V4. `ReconstruirConsolidadoService` publica `consolidado.reconstruido` com operadorId extraído do JWT em `AdminController`. |
