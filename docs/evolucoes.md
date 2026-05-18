---
tags:
  - evolucoes
  - backlog
  - arquitetura
---

# Evoluções Futuras e Backlog Técnico

**Papel:** 🧩 Arquiteto de Soluções · 🛠️ Engenheiro de Software  
**Data da análise:** 2026-05-18

Este documento registra o que foi deixado fora do escopo com justificativa técnica, e os gaps identificados na análise do código — confrontados linha a linha com a implementação real. Não é uma lista de desejos: cada item tem origem rastreável e caminho de evolução concreto.

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

O `application.properties` não referencia `spring.application.version`, e o `build-info` do Actuator não está configurado nos `build.gradle`.

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
O problema é de aparência, não de conteúdo arquitetural. Os diagramas renderizam no Structurizr Lite local (`docker compose up structurizr`) sem nenhuma limitação. A investigação de CSS customizado para o site-generatr exige tempo de leitura de uma ferramenta secundária, sem retorno proporcional neste momento.

**Caminho de evolução:**  
Injetar CSS customizado via `--custom-stylesheet` (opção da ferramenta, se disponível na versão atual), ou pós-processar o HTML com `sed`/`python` no step do workflow.

---

## Gaps identificados na análise do código

Estes itens não são decisões de escopo — são lacunas técnicas identificadas na leitura do código. Cada um tem severidade, risco concreto e caminho de mitigação.

---

### G-01 — Idempotência ausente no consumer do consolidado

**Severidade:** Alta  
**Serviço:** consolidado  
**Arquivo:** [`ProcessarLancamentoService.java`](../services/consolidado/src/main/java/br/com/carrefour/consolidado/application/usecase/ProcessarLancamentoService.java)

**O problema:**

O `ProcessarLancamentoService.executar(Command)` recebe um `Command` que contém `lancamentoId (UUID)`, mas não verifica se esse ID já foi processado antes de aplicar crédito ou débito ao saldo:

```java
@Override
@CacheEvict(value = "saldo-consolidado", key = "#command.dataCompetencia()")
@Transactional
public void executar(Command command) {
    var saldo = repository.buscarPorData(command.dataCompetencia())
            .orElseGet(() -> SaldoConsolidado.novo(command.dataCompetencia()));

    if (command.tipo() == TipoMovimento.CREDITO) {
        saldo.aplicarCredito(command.valor());  // ← não verifica se lancamentoId já foi aplicado
    } else {
        saldo.aplicarDebito(command.valor());
    }
    repository.salvar(saldo);
}
```

O RabbitMQ garante entrega *at-least-once*. Em caso de redelivery (falha de ack, restart do consumer, crash no meio do processamento), o mesmo evento chega duas vezes — e o saldo é duplicado.

**Risco concreto:**  
Redelivery em RabbitMQ é o comportamento padrão quando um consumer reconnecta sem ter dado ack. Em deploy rolling (duas instâncias rodando simultaneamente), o risco aumenta. O saldo duplicado passa pela reconciliação às 02:00 e gera divergência detectada — mas apenas no dia seguinte, e a correção exige `POST /admin/reconstruir`.

**Caminho de mitigação:**

Adicionar tracking de eventos processados:

```sql
-- V2__create_lancamentos_processados.sql
CREATE TABLE lancamentos_aplicados (
    lancamento_id UUID NOT NULL,
    data_competencia DATE NOT NULL,
    aplicado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_lancamentos_aplicados PRIMARY KEY (lancamento_id)
);
```

```java
// ProcessarLancamentoService.java
public void executar(Command command) {
    if (lancamentosAplicadosRepository.existsById(command.lancamentoId())) {
        log.atInfo().addKeyValue("event", "lancamento_ja_aplicado")
                    .addKeyValue("lancamento_id", command.lancamentoId())
                    .log("Evento ignorado — já aplicado (at-least-once delivery)");
        return;
    }
    // aplica crédito/débito + salva saldo
    // INSERT INTO lancamentos_aplicados (lancamento_id, ...)
}
```

A inserção em `lancamentos_aplicados` deve ocorrer na mesma transação do `salvar(saldo)` para garantir atomicidade.

---

### G-02 — LancamentosGateway sem resiliência

**Severidade:** Média  
**Serviço:** consolidado  
**Arquivo:** [`LancamentosGatewayAdapter.java`](../services/consolidado/src/main/java/br/com/carrefour/consolidado/adapter/out/gateway/LancamentosGatewayAdapter.java)

**O problema:**

O `LancamentosGatewayAdapter` usa `RestClient` sem `@Retry` ou `@CircuitBreaker`:

```java
public ResumoDiario buscarResumoDiario(LocalDate data) {
    return restClient.get()
            .uri("/registros/resumo?data={data}", data)
            .retrieve()
            .body(ResumoDiarioDto.class);  // ← falha propaga diretamente
}
```

Este gateway é usado por `ReconstruirConsolidadoService` (operação administrativa) e `ReconciliacaoDiariaJob` (job às 02:00). Falha transitória no serviço de lançamentos interrompe ambas as operações.

**Risco concreto:**  
Se o serviço de lançamentos sofrer spike de latência às 02:00 (durante o job de cleanup do outbox, que roda às 03:00, mas pode haver contenção antes), a reconciliação diária aborta sem registrar divergências. A janela de cegueira dura 24 horas até a próxima execução.

**Caminho de mitigação:**

Adicionar resilience4j ao gateway (já existe a dependência no `build.gradle` do consolidado):

```java
@Retry(name = "lancamentos-gateway")
@CircuitBreaker(name = "lancamentos-gateway", fallbackMethod = "resumoFallback")
public ResumoDiario buscarResumoDiario(LocalDate data) { ... }

private ResumoDiario resumoFallback(LocalDate data, Exception e) {
    log.atWarn().addKeyValue("event", "gateway_indisponivel")
                .addKeyValue("data", data)
                .log("Gateway de lançamentos indisponível — reconciliação parcial");
    throw new GatewayIndisponivel(data, e);
}
```

Configurar em `application.properties`:
```properties
resilience4j.retry.instances.lancamentos-gateway.max-attempts=3
resilience4j.retry.instances.lancamentos-gateway.wait-duration=2s
resilience4j.circuitbreaker.instances.lancamentos-gateway.sliding-window-size=5
resilience4j.circuitbreaker.instances.lancamentos-gateway.failure-rate-threshold=60
```

---

### G-03 — ReconciliacaoDiariaJob sem proteção contra execução concorrente

**Severidade:** Média (manifesta em deploy com múltiplas instâncias)  
**Serviço:** consolidado  
**Arquivo:** [`ReconciliacaoDiariaJob.java`](../services/consolidado/src/main/java/br/com/carrefour/consolidado/adapter/in/job/ReconciliacaoDiariaJob.java)

**O problema:**

```java
@Scheduled(cron = "0 0 2 * * *")
public void reconciliar() {
    // executa em TODAS as instâncias do pod simultaneamente
}
```

Em produção com 2+ réplicas do consolidado, o job executa em paralelo. O resultado é benigno para o job de reconciliação (que só lê e compara), mas o contador `saldo_reconciliado_divergencias_total` é incrementado N vezes para a mesma divergência — gerando alertas falsos.

A `CLAUDE.md` documenta que `docker-compose up --scale consolidado=3` é suportado. Esse cenário reproduz o problema imediatamente.

**Caminho de mitigação:**

ShedLock via PostgreSQL (sem dependência adicional — o banco já existe):

```groovy
// build.gradle
implementation 'net.javacrumbs.shedlock:shedlock-spring:5.14.0'
implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.14.0'
```

```java
@Scheduled(cron = "0 0 2 * * *")
@SchedulerLock(name = "reconciliacao-diaria", lockAtMostFor = "PT1H")
public void reconciliar() { ... }
```

Adicionar tabela `shedlock` via migration Flyway (fornecida pela biblioteca).

---

### G-04 — ReconstruirConsolidadoService com transação única para o período inteiro

**Severidade:** Baixa (operação raramente executada, sempre por ADMIN)  
**Serviço:** consolidado  
**Arquivo:** [`ReconstruirConsolidadoService.java`](../services/consolidado/src/main/java/br/com/carrefour/consolidado/application/usecase/ReconstruirConsolidadoService.java)

**O problema:**

A reconstrução de múltiplas datas ocorre dentro de uma única `@Transactional`. Se o gateway falhar no dia 15 de um período de 30 dias, os 14 dias já processados são revertidos pelo rollback:

```java
@Transactional  // ← uma transação para todo o período
public Resultado executar(Command command) {
    for (LocalDate data = command.dataInicio(); ...) {
        var resumo = gateway.buscarResumoDiario(data);  // pode falhar
        // salva saldo...
    }
}
```

**Risco concreto:**  
Baixo em operação normal. O endpoint é de uso administrativo (`ROLE_ADMIN`) e a janela de retentativa é de horas, não segundos. O operador pode reenviar a requisição com um período menor.

**Caminho de mitigação:**

Processar cada data em transação própria via bean auxiliar com `REQUIRES_NEW`:

```java
// ReconstruirConsolidadoService.java
for (LocalDate data : datas) {
    try {
        reconstrucaoPorData.executar(data);  // REQUIRES_NEW → commit imediato
        datasProcessadas++;
    } catch (Exception e) {
        log.atWarn().addKeyValue("data", data).log("Falha ao reconstruir data — continuando");
    }
}
```

---

### G-05 — Audit log ausente no consolidado para operações administrativas

**Severidade:** Baixa  
**Serviço:** consolidado

**O problema:**

O serviço de lançamentos tem `AuditEventListener` com `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, registrando toda operação em `audit_log`. O consolidado não tem equivalente. As operações `POST /admin/reconstruir` e divergências detectadas pela reconciliação não são auditadas.

Pergunta sem resposta rastreável: "quem reconstruiu o saldo do dia 2026-05-10, e a que horas?"

**Caminho de mitigação:**

Reutilizar o mesmo mecanismo: `AuditPublisher` port + `AuditEventListener` no consolidado. As operações de interesse são `ReconstruirConsolidadoService` (quem, período, datas processadas, divergências) e `ReconciliacaoDiariaJob` (divergências encontradas por data).

---

## Análise do status dos pendentes documentados

Durante o desenvolvimento foram registrados quatro itens como "pendentes para versões futuras" no `CLAUDE.md`. A análise do código confronta esse registro com o que está realmente implementado:

| Item documentado | Status real |
|------------------|-------------|
| Backoffice de DLQ | ✅ Correto — `DlqConsumer` só loga e mede; sem replay. Decisão correta. |
| Idempotência com payload diferente para estorno | ✅ **Resolvido** — `EstornarLancamentoService` deriva o ID do estorno deterministicamente (`UUID.nameUUIDFromBytes("estorno-" + originalId)`). Replay retorna 409 via colisão de PK. O "conflito de payload" não se aplica: estorno não tem payload variável. |
| Build info no MDC | ✅ Correto — não implementado, filtros de MDC não incluem `version` ou `commit_hash`. |
| C4 site — estética | ✅ Correto — HTML gerado sem CSS pelo `structurizr-site-generatr`. Conteúdo correto; aparência limitada. |

**Nota sobre o item de idempotência do estorno:** o item pode ser removido do backlog. O comportamento está correto e o mecanismo de idempotência está em vigor. Diferentemente do gap G-01 (consumer do consolidado), onde o `lancamentoId` não é verificado, o estorno é intrinsecamente idempotente pelo UUID derivado.

---

## Priorização para próxima iteração

| Prioridade | Gap | Razão |
|-----------|-----|-------|
| 1 | G-01 — Idempotência no consumer | Risco de corretude financeira — saldo pode ficar duplicado sem detecção imediata |
| 2 | G-02 — Gateway sem resiliência | Silencia falhas operacionais na reconciliação |
| 3 | G-03 — Job sem ShedLock | Alertas falsos em ambiente multi-instância |
| 4 | G-04 — Transação única na reconstrução | Operação administrativa raramente usada; workaround existe (período menor) |
| 5 | G-05 — Audit no consolidado | Conformidade; sem impacto em corretude ou disponibilidade |

G-01 deve ser implementado antes de qualquer deploy em produção com múltiplas instâncias do consolidado.
