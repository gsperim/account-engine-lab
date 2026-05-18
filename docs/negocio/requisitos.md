---
tags:
  - negocio
  - requisitos
---

# Requisitos Funcionais e Não Funcionais

**Papel:** 💼 Arquiteto de Negócios · 🧩 Arquiteto de Soluções
**Framework:** ArchiMate — Motivation View (Requirement, Constraint)

---

## Sumário de Requisitos Funcionais

| ID | Descrição resumida | Serviço | Driver |
|----|-------------------|---------|--------|
| [RF-01](#rf-01) | Registrar lançamento (débito ou crédito) | Lançamentos | [D-01](drivers.md#d-01) |
| [RF-02](#rf-02) | Consultar lançamentos por período | Lançamentos | [D-01](drivers.md#d-01) |
| [RF-03](#rf-03) | Consultar saldo consolidado de um dia | Consolidação | [D-02](drivers.md#d-02) |
| [RF-04](#rf-04) | Atualizar saldo consolidado após cada lançamento | Consolidação | [D-02](drivers.md#d-02), [D-05](drivers.md#d-05) |
| [RF-05](#rf-05) | Validar e rejeitar lançamentos inválidos | Lançamentos | [D-01](drivers.md#d-01) |
| 🔹 [RF-06](#rf-06) | Reconciliar totais do consolidado com os lançamentos | Consolidação | [D-05](drivers.md#d-05) |
| 🔹 [RF-07](#rf-07) | Solicitar recálculo assíncrono de totais por período | Lançamentos | [D-07](drivers.md#d-07) |
| 🔹 [RF-08](#rf-08) | Registrar estorno rastreável de lançamento | Lançamentos | [D-01](drivers.md#d-01) |
| 🔹 [RF-09](#rf-09) | Consultar consolidação por período e granularidade | Consolidação | [D-02](drivers.md#d-02) |

> 🔹 Requisitos marcados com este símbolo vão além do enunciado original e são práticas comuns em sistemas financeiros de produção.

---

## Requisitos Funcionais Detalhados

### RF-01 — Registrar Lançamento { #rf-01 }

**Serviço:** Lançamentos · **Driver:** [D-01](drivers.md#d-01)

**Campos de entrada:**

| Campo | Tipo | Obrigatório | Regras |
|-------|------|-------------|--------|
| `tipo` | enum | Sim | Valores aceitos: `debito`, `credito` |
| `valor` | decimal | Sim | Maior que zero; até duas casas decimais |
| `data_competencia` | date | Sim | Formato ISO 8601 (`YYYY-MM-DD`); aceita datas passadas e futuras |
| `descricao` | string | Sim | Entre 3 e 255 caracteres |

**Campos de saída (sucesso):**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | uuid | Identificador único gerado pelo sistema |
| `tipo` | enum | Tipo confirmado |
| `valor` | decimal | Valor confirmado |
| `data_competencia` | date | Data de competência confirmada |
| `descricao` | string | Descrição confirmada |
| `criado_em` | datetime | Timestamp UTC de criação do registro |
| `estorno_de` | uuid \| null | Preenchido se este lançamento é um estorno — ID do lançamento original; `null` caso contrário |
| `estornado_por` | uuid \| null | Preenchido se este lançamento foi estornado — ID do lançamento de estorno; `null` caso contrário |

**Regras de negócio:**
- Lançamentos são **imutáveis** após confirmação — não podem ser editados nem excluídos. Para corrigir um lançamento, registra-se um lançamento compensatório.
- O sistema deve publicar o evento `LancamentoRegistrado` somente após a persistência confirmada.
- A confirmação ao cliente (HTTP 201) deve ocorrer somente após persistência bem-sucedida.

**Casos de borda:**

| Situação | Comportamento esperado |
|----------|----------------------|
| `valor` = 0 | Rejeitar — HTTP 422 |
| `valor` negativo | Rejeitar — HTTP 422 |
| `tipo` fora do enum | Rejeitar — HTTP 422 |
| `data_competencia` futura | Aceitar — lançamento agendado é válido |
| `data_competencia` muito antiga | Aceitar — sem restrição de janela temporal |
| `descricao` com menos de 3 caracteres | Rejeitar — HTTP 422 |
| Falha na publicação do evento | Registrar lançamento e garantir reentrega via mecanismo de retry/outbox |

**Critérios de aceite:**

- [ ] Dado um lançamento válido, deve retornar HTTP 201 com o recurso criado
- [ ] Dado qualquer campo obrigatório ausente, deve retornar HTTP 422 com mensagem descritiva
- [ ] Dado `valor` ≤ 0, deve retornar HTTP 422
- [ ] Dado `tipo` inválido, deve retornar HTTP 422
- [ ] O evento `LancamentoRegistrado` deve ser publicado após cada registro bem-sucedido
- [ ] Uma falha no broker não deve impedir o registro do lançamento
- [ ] Os campos `estorno_de` e `estornado_por` devem ser `null` para lançamentos comuns recém-criados

**Fluxo de dados:**

```mermaid
sequenceDiagram
    actor C as Caixa / PDV
    participant GW as API Gateway
    participant LA as Lançamentos
    participant DB as PostgreSQL
    participant OB as OutboxRelay
    participant BR as RabbitMQ

    C->>GW: POST /lancamentos/registros<br/>Idempotency-Key: UUID · Bearer token
    GW->>GW: valida JWT (JWKS Keycloak)
    GW->>LA: POST /registros (Bearer passthrough)
    LA->>LA: valida JWT + extrai operadorId (sub)
    LA->>LA: verifica ROLE_CAIXA ou ROLE_PDV
    LA->>LA: valida campos (tipo, valor, data_competencia)
    LA->>DB: existsById(idempotencyKey)?
    alt já existe — mesmo payload
        DB-->>LA: true
        LA-->>C: HTTP 200 (replay idempotente)
    else já existe — payload diferente
        LA-->>C: HTTP 409 IDEMPOTENCY_KEY_CONFLITO
    else novo lançamento
        DB-->>LA: false
        LA->>DB: INSERT lancamento + INSERT outbox (transação atômica)
        DB-->>LA: commit
        LA-->>C: HTTP 201 {id, tipo, valor, criado_em}
    end

    note over OB,BR: Fluxo assíncrono (OutboxRelay a cada 5s)
    OB->>DB: busca outbox WHERE publicado = false
    OB->>BR: publica LancamentoRegistrado
    OB->>DB: marca publicado = true
```

---

### RF-02 — Consultar Lançamentos por Período { #rf-02 }

**Serviço:** Lançamentos · **Driver:** [D-01](drivers.md#d-01)

**Campos de entrada:**

| Campo | Tipo | Obrigatório | Regras |
|-------|------|-------------|--------|
| `data_inicio` | date | Sim | Formato ISO 8601 |
| `data_fim` | date | Sim | Formato ISO 8601; deve ser ≥ `data_inicio` |
| `tipo` | enum | Não | Filtro opcional: `debito` ou `credito` |
| `pagina` | integer | Não | Padrão: 1 |
| `tamanho` | integer | Não | Padrão: 20; máximo: 100 |

**Campos de saída:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `itens` | array | Lista de lançamentos — cada item segue o schema abaixo |
| `itens[].id` | uuid | Identificador do lançamento |
| `itens[].tipo` | enum | `debito` ou `credito` |
| `itens[].valor` | decimal | Valor do lançamento |
| `itens[].data_competencia` | date | Data de competência |
| `itens[].descricao` | string | Descrição do lançamento |
| `itens[].criado_em` | datetime | Timestamp UTC de criação |
| `itens[].estorno_de` | uuid \| null | ID do lançamento original, se este item é um estorno |
| `itens[].estornado_por` | uuid \| null | ID do estorno, se este item foi estornado |
| `total` | integer | Total de registros no período (sem paginação) |
| `pagina` | integer | Página atual |
| `tamanho` | integer | Tamanho da página |

**Regras de negócio:**
- Ordenação padrão: `data_competencia` crescente, `criado_em` crescente como desempate.

**Casos de borda:**

| Situação | Comportamento esperado |
|----------|----------------------|
| Período sem lançamentos | Retornar HTTP 200 com `itens: []` e `total: 0` |
| `data_inicio` > `data_fim` | Rejeitar — HTTP 422 |
| Período muito amplo (anos) | Aceitar — paginação garante desempenho |

**Critérios de aceite:**

- [ ] Dado um período válido, deve retornar HTTP 200 com a lista paginada
- [ ] Dado período sem lançamentos, deve retornar HTTP 200 com lista vazia (não 404)
- [ ] Dado `data_inicio` > `data_fim`, deve retornar HTTP 422
- [ ] O filtro por `tipo` deve funcionar em combinação com o período

---

### RF-03 — Consultar Saldo Consolidado Diário { #rf-03 }

**Serviço:** Consolidação · **Driver:** [D-02](drivers.md#d-02)

**Campos de entrada:**

| Campo | Tipo | Obrigatório | Regras |
|-------|------|-------------|--------|
| `data` | date | Sim | Formato ISO 8601 |

**Campos de saída:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `data` | date | Data de referência consultada |
| `total_creditos` | decimal | Soma de todos os créditos do dia |
| `total_debitos` | decimal | Soma de todos os débitos do dia |
| `saldo` | decimal | `total_creditos` − `total_debitos`; pode ser negativo |
| `atualizado_em` | datetime | Timestamp UTC da última atualização do consolidado |

**Regras de negócio:**
- O saldo pode ser negativo — não há restrição de saldo mínimo.
- O consolidado é **eventual** — um lançamento registrado pode levar alguns segundos para aparecer no saldo consultado.

**Casos de borda:**

| Situação | Comportamento esperado |
|----------|----------------------|
| Data sem lançamentos | Retornar HTTP 200 com todos os valores zerados (não 404) |
| Data futura | Retornar HTTP 200 com valores zerados |
| Consolidado ainda não processado para a data | Retornar HTTP 200 com os dados disponíveis até o momento |

**Critérios de aceite:**

- [ ] Dado uma data com lançamentos, deve retornar HTTP 200 com os totais corretos
- [ ] Dado uma data sem lançamentos, deve retornar HTTP 200 com zeros (não 404)
- [ ] O saldo deve ser igual a `total_creditos` − `total_debitos`
- [ ] Dado `data` ausente, deve retornar HTTP 422

---

### RF-04 — Atualizar Consolidado após Lançamento { #rf-04 }

**Serviço:** Consolidação · **Driver:** [D-02](drivers.md#d-02), [D-05](drivers.md#d-05)

**Triggers:** dois eventos consumidos via broker, cada um com handler dedicado:

| Evento | Handler | Responsabilidade |
|--------|---------|-----------------|
| `LancamentoRegistrado` | Handler A | Inserir lançamento + recalcular saldo do dia |
| `LancamentoEstornado` | Handler B | Inserir estorno + vincular original + recalcular saldo do dia |

**Comportamento:**
- Ao receber qualquer um dos eventos, deve recalcular e persistir o saldo do dia correspondente à `data_competencia`.
- O processamento deve ser **idempotente** — processar o mesmo evento mais de uma vez não deve alterar o resultado.
- O processamento deve garantir **at-least-once delivery** — nenhum evento pode ser descartado sem processamento.

**Regras de negócio:**
- A indisponibilidade do Serviço de Consolidação Diária não deve gerar perda de eventos — o broker retém as mensagens até o serviço estar disponível novamente.
- O consolidado de uma data deve sempre refletir a soma de **todos** os lançamentos e estornos daquela `data_competencia`.

**Handler A — `LancamentoRegistrado`:**

```sql
-- Inserção idempotente do lançamento
INSERT INTO lancamentos_processados (id, tipo, valor, data_competencia)
VALUES (:event_id, :tipo, :valor, :data)
ON CONFLICT (id) DO NOTHING;

-- Recálculo do saldo por agregação
UPDATE consolidacao_diaria
SET total_creditos = (SELECT COALESCE(SUM(valor), 0) FROM lancamentos_processados
                      WHERE data_competencia = :data AND tipo = 'credito'),
    total_debitos  = (SELECT COALESCE(SUM(valor), 0) FROM lancamentos_processados
                      WHERE data_competencia = :data AND tipo = 'debito'),
    atualizado_em  = NOW()
WHERE data = :data;
```

**Handler B — `LancamentoEstornado`:**

```sql
-- 1. Inserção idempotente do estorno (tipo já é inverso ao original)
INSERT INTO lancamentos_processados (id, tipo, valor, data_competencia, estorno_de)
VALUES (:estorno_id, :tipo_inverso, :valor, :data, :id_original)
ON CONFLICT (id) DO NOTHING;

-- 2. Vínculo no registro original (idempotente: só atualiza se ainda não vinculado)
UPDATE lancamentos_processados
SET estornado_por = :estorno_id
WHERE id = :id_original
  AND estornado_por IS NULL;

-- 3. Recálculo do saldo (mesmo que handler A)
UPDATE consolidacao_diaria
SET total_creditos = (SELECT COALESCE(SUM(valor), 0) FROM lancamentos_processados
                      WHERE data_competencia = :data AND tipo = 'credito'),
    total_debitos  = (SELECT COALESCE(SUM(valor), 0) FROM lancamentos_processados
                      WHERE data_competencia = :data AND tipo = 'debito'),
    atualizado_em  = NOW()
WHERE data = :data;
```

A separação em dois handlers mantém cada um com responsabilidade única. O recálculo do saldo (passo 3) é uma função compartilhada entre os dois. A idempotência é garantida pelo `ON CONFLICT DO NOTHING` no insert e pela condição `estornado_por IS NULL` no update do original.

**Critérios de aceite:**

- [ ] Dado um evento `LancamentoRegistrado` recebido, o saldo do dia correspondente deve ser atualizado
- [ ] Dado um evento `LancamentoEstornado` recebido, o saldo do dia deve ser atualizado e o registro original deve ter `estornado_por` preenchido
- [ ] Dado o mesmo evento processado duas vezes, o saldo não deve ser duplicado (idempotência)
- [ ] Dado o serviço indisponível temporariamente, os eventos devem ser processados após a recuperação

**Fluxo de dados:**

```mermaid
sequenceDiagram
    participant BR as RabbitMQ
    participant CO as LancamentoEventoConsumer
    participant PL as ProcessarLancamentoService
    participant AP as lancamentos_aplicados
    participant DB as PostgreSQL
    participant RD as Redis

    BR->>CO: LancamentoRegistrado<br/>{lancamentoId, tipo, valor, data_competencia}
    CO->>CO: converte TipoMovimento (Anti-Corruption Layer)
    CO->>PL: executar(Command)
    PL->>AP: existePorId(lancamentoId)?
    alt já aplicado — replay at-least-once
        AP-->>PL: true
        PL-->>CO: retorna sem efeito
        CO-->>BR: ACK
    else primeiro processamento
        AP-->>PL: false
        PL->>DB: busca ou cria SaldoConsolidado(data_competencia)
        PL->>PL: aplicarCredito() ou aplicarDebito()
        PL->>DB: salva saldo + INSERT lancamentos_aplicados<br/>(transação atômica)
        DB-->>PL: commit
        PL->>RD: @CacheEvict (invalida cache do dia)
        CO-->>BR: ACK
    end
```

---

### RF-05 — Validar Lançamentos { #rf-05 }

**Serviço:** Lançamentos · **Driver:** [D-01](drivers.md#d-01)

Detalhado como parte das regras e casos de borda do [RF-01](#rf-01). A validação ocorre antes da persistência e é síncrona — o cliente recebe a resposta de erro imediatamente.

**Regras consolidadas de validação:**

| Campo | Condição de rejeição | Código HTTP |
|-------|---------------------|-------------|
| `tipo` | Ausente ou fora do enum | 422 |
| `valor` | Ausente, zero ou negativo | 422 |
| `data_competencia` | Ausente ou formato inválido | 422 |
| `descricao` | Ausente ou com menos de 3 caracteres | 422 |

**Critérios de aceite:**

- [ ] Toda rejeição deve retornar HTTP 422 com mensagem que identifica o campo inválido
- [ ] Múltiplos campos inválidos devem ser reportados em uma única resposta

---

## Requisitos Funcionais Detalhados — Escopo Diferencial

> 🔹 Os requisitos abaixo não constam no enunciado original. São práticas comuns em sistemas financeiros de produção: rastreabilidade de correções, recuperação de desastres, integridade contínua e análise de tendências.

### RF-06 — Reconciliação Periódica 🔹 { #rf-06 }

**Serviço:** Consolidação · **Driver:** [D-05](drivers.md#d-05)

**Trigger:** Agendamento diário automático ou invocação manual via `POST /consolidacao/reconciliacao`

**Comportamento:**
- Para cada data com registros em `lancamentos_processados`, recalcular o saldo esperado via `SELECT SUM`
- Comparar o resultado com o valor em `consolidacao_diaria`
- Emitir alerta operacional (log estruturado nível `ERROR` + métrica) para cada divergência encontrada

**Regras de negócio:**
- Dias sem lançamentos retornam saldo zero — não são considerados divergência
- A reconciliação não altera dados — apenas detecta e alerta; correção é feita via [RF-07](#rf-07)

**Critérios de aceite:**

- [ ] Dado saldos consistentes, a reconciliação deve completar sem alertas
- [ ] Dado uma divergência real, deve gerar alerta com a data afetada e os valores divergentes
- [ ] Dado dias sem lançamentos, não deve gerar alertas falsos positivos
- [ ] A reconciliação deve ser idempotente — re-execução não gera alertas duplicados

**Fluxo de dados:**

```mermaid
sequenceDiagram
    participant SCH as Scheduler (02:00)
    participant JOB as ReconciliacaoDiariaJob
    participant GW as LancamentosGateway
    participant LA as Serviço de Lançamentos
    participant DB as PostgreSQL (Consolidado)
    participant PR as Prometheus

    SCH->>JOB: dispara (@Scheduled cron="0 0 2 * * *")
    loop Para cada data com consolidado
        JOB->>GW: buscarResumo(data)
        GW->>LA: GET /registros/resumo?data=YYYY-MM-DD
        LA-->>GW: {total_creditos, total_debitos, total_lancamentos}
        GW-->>JOB: ResumoLancamentosDto
        JOB->>DB: SELECT FROM saldo_consolidado WHERE data = :data
        DB-->>JOB: SaldoConsolidado
        JOB->>JOB: compara totais
        alt divergência detectada
            JOB->>PR: saldo_reconciliado_divergencias_total++
            JOB->>JOB: log ERROR {data, esperado, encontrado}
        else consistente
            JOB->>PR: saldo_reconciliado_total++
        end
    end
```

---

### RF-07 — Recálculo Assíncrono de Totais 🔹 { #rf-07 }

**Serviço:** Lançamentos · **Driver:** [D-07](drivers.md#d-07)

**Campos de entrada:**

| Campo | Tipo | Obrigatório | Regras |
|-------|------|-------------|--------|
| `data_inicio` | date | Sim | Formato ISO 8601 |
| `data_fim` | date | Sim | Formato ISO 8601; deve ser ≥ `data_inicio` |

**Campos de saída (imediato — HTTP 202):**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `job_id` | uuid | Identificador da solicitação de recálculo |
| `status` | string | `aceito` — processamento ocorre de forma assíncrona |

**Comportamento:**
- Para cada dia com lançamentos no intervalo, calcular `SUM(creditos)` e `SUM(debitos)` e publicar um evento `TotaisDiarioCalculado` no broker
- O Serviço de Consolidação Diária consome os eventos e reconstrói seu estado com os mesmos mecanismos idempotentes do [RF-04](#rf-04)
- Dias sem lançamentos no intervalo não geram eventos

**Regras de negócio:**
- Eventos são publicados em ordem cronológica crescente
- Re-solicitação do mesmo intervalo é idempotente — a Consolidação absorve sem duplicar valores

**Critérios de aceite:**

- [ ] Dado um intervalo válido, deve retornar HTTP 202 imediatamente com `job_id`
- [ ] Dado `data_inicio` > `data_fim`, deve retornar HTTP 422
- [ ] Para cada dia com lançamentos no intervalo, deve ser publicado exatamente um evento `TotaisDiarioCalculado`
- [ ] Dias sem lançamentos não devem gerar eventos

**Fluxo de recuperação:**

```mermaid
sequenceDiagram
    actor Gestor
    participant GW as API Gateway
    participant LA as Serviço de Lançamentos
    participant DB as PostgreSQL (Lançamentos)
    participant BR as Message Broker
    participant CO as Serviço de Consolidação

    Gestor->>GW: POST /lancamentos/recalcular<br/>{data_inicio, data_fim}
    GW->>LA: POST /recalcular (JWT validado)
    LA-->>Gestor: HTTP 202 {job_id, status: "aceito"}

    loop Para cada dia com lançamentos no intervalo
        LA->>DB: SELECT SUM(creditos), SUM(debitos)<br/>WHERE data_competencia = :dia
        DB-->>LA: totais do dia
        LA->>BR: publica TotaisDiarioCalculado<br/>{data, total_creditos, total_debitos, job_id}
    end

    BR->>CO: entrega TotaisDiarioCalculado (at-least-once)
    CO->>CO: reconstrói saldo do dia<br/>(idempotente via job_id)
```

---

### RF-08 — Registrar Estorno Rastreável 🔹 { #rf-08 }

**Serviço:** Lançamentos · **Driver:** [D-01](drivers.md#d-01)

**Campos de entrada:**

| Campo | Tipo | Obrigatório | Regras |
|-------|------|-------------|--------|
| `id_lancamento_original` | uuid | Sim | Deve existir e não pode ser um estorno |
| `motivo` | string | Sim | Entre 3 e 255 caracteres |

**Campos de saída (sucesso):**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | uuid | Identificador do estorno |
| `tipo` | enum | Tipo inverso ao original: `credito` vira `debito` e vice-versa |
| `valor` | decimal | Mesmo valor do lançamento original |
| `data_competencia` | date | Mesma data de competência do original |
| `descricao` | string | Descrição gerada automaticamente referenciando o lançamento original |
| `estorno_de` | uuid | ID do lançamento original — vínculo explícito e rastreável |
| `estornado_por` | null | Sempre `null` — um estorno não pode ser estornado novamente ([RF-08](#rf-08)) |
| `motivo` | string | Motivo registrado |
| `criado_em` | datetime | Timestamp UTC do estorno |

**Regras de negócio:**
- Valor e data de competência são sempre idênticos ao original — estorno parcial não é permitido
- Um lançamento que já **é** um estorno (`estorno_de != null`) não pode ser estornado novamente
- Um lançamento que já **foi** estornado (`estornado_por != null`) não pode ser estornado novamente — evita duplo estorno
- O estorno publica **exclusivamente** o evento `LancamentoEstornado` após persistência confirmada — não publica `LancamentoRegistrado`

**Critérios de aceite:**

- [ ] Dado um lançamento de crédito estornado, deve criar um débito com o mesmo valor na mesma data de competência
- [ ] O campo `estorno_de` deve apontar para o `id` do lançamento original
- [ ] Dado tentativa de estornar um lançamento que já **é** um estorno, deve retornar HTTP 422
- [ ] Dado tentativa de estornar um lançamento que já **foi** estornado (`estornado_por` preenchido), deve retornar HTTP 422
- [ ] Dado `id_lancamento_original` inexistente, deve retornar HTTP 404

**Fluxo de dados:**

```mermaid
sequenceDiagram
    actor C as Caixa
    participant GW as API Gateway
    participant LA as Lançamentos
    participant DB as PostgreSQL
    participant OB as OutboxRelay
    participant BR as RabbitMQ

    C->>GW: POST /registros/{id}/estorno · Bearer token
    GW->>GW: valida JWT (JWKS Keycloak)
    GW->>LA: POST /registros/{id}/estorno (Bearer passthrough)
    LA->>LA: valida JWT + verifica ROLE_CAIXA
    LA->>DB: busca lançamento original por id
    alt id não encontrado
        DB-->>LA: null
        LA-->>C: HTTP 404
    else encontrado
        DB-->>LA: lançamento original
        LA->>LA: estorno_de != null? → HTTP 422 (estorno de estorno)
        LA->>LA: estornado_por != null? → HTTP 422 (duplo estorno)
        LA->>LA: cria estorno com UUID derivado (idempotente)
        LA->>DB: INSERT estorno + UPDATE original.estornado_por<br/>+ INSERT outbox (transação atômica)
        DB-->>LA: commit
        LA-->>C: HTTP 201 {id_estorno, estorno_de, tipo_inverso}
    end

    note over OB,BR: Fluxo assíncrono
    OB->>DB: busca outbox WHERE publicado = false
    OB->>BR: publica LancamentoEstornado (não LancamentoRegistrado)
    OB->>DB: marca publicado = true
```

---

### RF-09 — Consultar Consolidação por Período e Granularidade 🔹 { #rf-09 }

**Serviço:** Consolidação · **Driver:** [D-02](drivers.md#d-02)

**Campos de entrada:**

| Campo | Tipo | Obrigatório | Regras |
|-------|------|-------------|--------|
| `data_inicio` | date | Sim | Formato ISO 8601 |
| `data_fim` | date | Sim | Formato ISO 8601; deve ser ≥ `data_inicio` |
| `granularidade` | enum | Não | `dia` (padrão), `semana`, `mes` |

**Campos de saída:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `periodos` | array | Lista de períodos consolidados |
| `periodos[].periodo` | string | Identificação do período (ex: `2024-01-15`, `2024-W03`, `2024-01`) |
| `periodos[].total_creditos` | decimal | Soma de créditos no período |
| `periodos[].total_debitos` | decimal | Soma de débitos no período |
| `periodos[].saldo` | decimal | `total_creditos` − `total_debitos` |

**Regras de negócio:**
- `semana` segue o padrão ISO 8601 (semana começa na segunda-feira)
- Períodos sem lançamentos retornam zeros — não são omitidos da resposta

**Critérios de aceite:**

- [ ] Dado granularidade `dia`, deve retornar um registro por dia no intervalo
- [ ] Dado granularidade `semana`, deve retornar um registro por semana ISO
- [ ] Dado granularidade `mes`, deve retornar um registro por mês
- [ ] Dado `data_inicio` > `data_fim`, deve retornar HTTP 422

---

## Requisitos Não Funcionais

| ID | Requisito | Categoria | Métrica / Meta | Driver |
|----|-----------|-----------|---------------|--------|
| <span id="nfr-01"></span>NFR-01 | O Serviço de Lançamentos não pode ficar indisponível se o Serviço de Consolidação Diária cair | Resiliência | Disponibilidade do Lançamentos independe da Consolidação | [D-03](drivers.md#d-03) |
| <span id="nfr-02"></span>NFR-02 | O Serviço de Consolidação Diária deve suportar picos de carga | Performance | 50 req/s com no máximo 5% de perda | [D-04](drivers.md#d-04) |
| <span id="nfr-03"></span>NFR-03 | Lançamentos registrados não podem ser perdidos em cenários de falha | Confiabilidade | Zero perda de lançamentos confirmados (at-least-once delivery) | [D-05](drivers.md#d-05) |
| <span id="nfr-04"></span>NFR-04 | O sistema deve ser observável | Observabilidade | Logs estruturados, métricas e traces em 100% das requisições | [D-04](drivers.md#d-04) |
| <span id="nfr-05"></span>NFR-05 | Comunicação entre serviços deve ser autenticada e autorizada | Segurança | Zero comunicação sem autenticação válida | — |
| <span id="nfr-06"></span>NFR-06 | Eventos que falham após esgotamento de retentativas devem ser preservados para análise | Confiabilidade | Dead Letter Queue (DLQ) configurada; zero descarte silencioso de eventos | [D-05](drivers.md#d-05) |
| <span id="nfr-07"></span>NFR-07 | O Serviço de Consolidação Diária deve proteger-se contra sobrecarga de requisições | Resiliência | Rate limiting na borda da API; excedentes recebem HTTP 429 | [D-04](drivers.md#d-04), [NFR-02](#nfr-02) |
| <span id="nfr-08"></span>NFR-08 | Falhas transientes não devem resultar em perda definitiva de operações | Confiabilidade | Retry com exponential backoff e jitter; máximo configurável de tentativas | [D-05](drivers.md#d-05) |
| <span id="nfr-09"></span>🔹 NFR-09 | Toda operação de escrita deve gerar trilha de auditoria imutável | Compliance | 100% das escritas com registro de identidade, timestamp UTC e recurso afetado | [D-06](drivers.md#d-06) |
| <span id="nfr-10"></span>🔹 NFR-10 | O estado da Consolidação Diária deve ser reconstruível a partir do zero | Resiliência | Reconstrução completa via [RF-07](#rf-07) sem acesso direto ao banco do Lançamentos | [D-07](drivers.md#d-07) |

---

## Restrições

| ID | Restrição | Origem |
|----|-----------|--------|
| <span id="c-01"></span>C-01 | A solução deve ser executável localmente via `docker-compose` | Requisito obrigatório do desafio |
| <span id="c-02"></span>C-02 | O repositório deve ser público no GitHub com toda a documentação | Requisito obrigatório do desafio |
| <span id="c-03"></span>C-03 | A linguagem de implementação é livre | Requisito do desafio |
| <span id="c-04"></span>🔹 C-04 | Dados pessoais e financeiros devem obedecer à LGPD (Lei 13.709/2018): prazo de retenção conforme regulação, direito de exclusão via anonimização quando dados não puderem ser apagados | [D-08](drivers.md#d-08) |

--- 

## Rastreabilidade

=== "Requisitos Funcionais"

    <div class="grid cards" markdown>

    - :material-cash-plus: [D-01](drivers.md#d-01) — Ausência de registro estruturado

        ---

        [RF-01](#rf-01) Registrar lançamento  
        [RF-02](#rf-02) Consultar lançamentos por período  
        [RF-05](#rf-05) Validar lançamentos  
        🔹 [RF-08](#rf-08) Registrar estorno rastreável

        **Serviço:** Lançamentos

    - :material-chart-bar: [D-02](drivers.md#d-02) — Impossibilidade de visualizar saldo

        ---

        [RF-03](#rf-03) Consultar saldo consolidado  
        [RF-04](#rf-04) Atualizar consolidado após lançamento  
        🔹 [RF-09](#rf-09) Consultar por período e granularidade

        **Serviço:** Consolidação

    - :material-shield-check: [D-05](drivers.md#d-05) — Lançamentos não podem ser perdidos

        ---

        [RF-04](#rf-04) Atualizar consolidado após lançamento  
        🔹 [RF-06](#rf-06) Reconciliação periódica

        **Mecanismo:** Outbox Pattern ([ADR-003](../adr/ADR-003-outbox-pattern.md)) garante at-least-once delivery  
        **Serviço:** Consolidação

    - :material-history: [D-07](drivers.md#d-07) — Recovery sem perda total de estado 🔹

        ---

        🔹 [RF-07](#rf-07) Recálculo assíncrono de totais

        **Serviço:** Lançamentos

    </div>

=== "Requisitos Não Funcionais"

    <div class="grid cards" markdown>

    - :material-lan-disconnect: [D-03](drivers.md#d-03) — Dependência entre serviços

        ---

        [NFR-01](#nfr-01) Lançamentos independe da consolidação

        **Decisões:** [ADR-001](../adr/ADR-001-padrao-arquitetural.md) · [ADR-002](../adr/ADR-002-message-broker.md)  
        **Componente:** Mensageria

    - :material-speedometer: [D-04](drivers.md#d-04) — Picos de carga no consolidado

        ---

        [NFR-02](#nfr-02) 50 req/s · 5% perda máx  
        [NFR-04](#nfr-04) Logs estruturados, métricas e traces em 100% das requisições  
        [NFR-07](#nfr-07) Rate limiting na borda da API

        **Decisões:** [ADR-007](../adr/ADR-007-api-gateway.md) · [ADR-009](../adr/ADR-009-api-gateway-producao.md) · [ADR-015](../adr/ADR-015-observabilidade.md)  
        **Componente:** Consolidação + Cache + Rate Limiting + Observabilidade

    - :material-email-fast: [D-05](drivers.md#d-05) — Lançamentos não podem ser perdidos

        ---

        [NFR-03](#nfr-03) Zero perda · at-least-once delivery  
        [NFR-06](#nfr-06) DLQ para eventos com falha  
        [NFR-08](#nfr-08) Retry com backoff e jitter

        **Decisões:** [ADR-002](../adr/ADR-002-message-broker.md) · [ADR-003](../adr/ADR-003-outbox-pattern.md) (Outbox Pattern)  
        **Componente:** Mensageria + Persistência

    - :material-file-document-check: [D-06](drivers.md#d-06) — Rastreabilidade de operações 🔹

        ---

        🔹 [NFR-09](#nfr-09) Trilha de auditoria imutável  
        [NFR-05](#nfr-05) Comunicação autenticada e autorizada *(requisito transversal)*

        **Decisões:** [ADR-004](../adr/ADR-004-jwt-validacao-local.md) · [ADR-010](../adr/ADR-010-seguranca.md) · [ADR-014](../adr/ADR-014-identity-provider.md) · [ADR-016](../adr/ADR-016-redacao-pii-logs.md)  
        **Componente:** Lançamentos + Consolidação

    - :material-backup-restore: [D-07](drivers.md#d-07) — Recovery sem perda total de estado 🔹

        ---

        🔹 [NFR-10](#nfr-10) Reconstrução da Consolidação via [RF-07](#rf-07)

        **Decisões:** [ADR-003](../adr/ADR-003-outbox-pattern.md) · [ADR-012](../adr/ADR-012-persistencia.md)  
        **Componente:** Serviço de Lançamentos

    </div>
