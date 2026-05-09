---
tags:
  - dados
  - persistencia
  - schema
---

# Dados e Persistência

**Perspectiva:** 📊 Arquiteto de Dados · 🔗 Arquiteto de Integração  
**Framework:** ArchiMate — Application Layer (data objects) + C4 L2/L3  
**Decisões:** [ADR-001](../adr/ADR-001-padrao-arquitetural.md) (database per service) · [ADR-003](../adr/ADR-003-outbox-pattern.md) (Outbox) · [ADR-012](../adr/ADR-012-persistencia.md) (mecanismos de persistência)

---

## Visão Geral

O sistema adota **database per service**: cada serviço tem seu próprio banco de dados isolado e nenhum serviço acessa diretamente o banco do outro. A consistência entre os serviços é eventual, mediada pelo broker de mensagens.

```mermaid
flowchart LR
    subgraph LA["Serviço de Lançamentos"]
        direction TB
        API_L["REST API"]
        DB_L[("PostgreSQL\nlancamentos")]
        OB[("Outbox")]
        RELAY["Outbox Relay\n(polling)"]
    end

    subgraph CO["Serviço de Consolidação"]
        direction TB
        API_C["REST API"]
        DB_C[("PostgreSQL\nconsolidado")]
        CACHE[("Redis\ncache-aside")]
    end

    BR["RabbitMQ\n(broker)"]

    API_L -->|"INSERT (atômico)"| DB_L
    API_L -->|"INSERT (mesma tx)"| OB
    RELAY -->|"polling"| OB
    RELAY -->|"publish"| BR
    BR -->|"consume\nat-least-once"| CO
    CO -->|"upsert idempotente"| DB_C
    CO -->|"invalida"| CACHE
    API_C -->|"cache-aside read"| CACHE
    CACHE -.->|"miss → lê"| DB_C
```

---

## Serviço de Lançamentos — PostgreSQL

### Tabela `lancamentos`

```sql
CREATE TABLE lancamentos (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo             VARCHAR(10) NOT NULL CHECK (tipo IN ('debito', 'credito')),
    valor            NUMERIC(15,2) NOT NULL CHECK (valor > 0),
    data_competencia DATE        NOT NULL,
    descricao        VARCHAR(255) NOT NULL CHECK (LENGTH(descricao) >= 3),
    estorno_de       UUID        REFERENCES lancamentos(id),
    estornado_por    UUID        REFERENCES lancamentos(id),
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**Decisões de design:**

| Escolha | Alternativa descartada | Motivo |
|---------|----------------------|--------|
| `NUMERIC(15,2)` | `FLOAT` / `DOUBLE PRECISION` | Precisão exata para valores financeiros — `FLOAT` acumula erro de ponto flutuante |
| `UUID` como PK | `SERIAL` (bigint auto-increment) | UUIDs gerados pelo serviço: sem risco de vazamento de sequência, portáveis entre ambientes |
| `gen_random_uuid()` | UUID gerado na aplicação | Simplifica o INSERT (sem necessidade de gerar na camada de aplicação), garante unicidade no banco |
| `estorno_de` / `estornado_por` | Tabela separada de estornos | Rastreabilidade bidirecional na mesma linha — queries mais simples, integridade referencial nativa |
| Lançamentos imutáveis (append-only) | UPDATE / DELETE | Imutabilidade garante trilha de auditoria — correções via estorno, nunca via edição ([RF-08](../negocio/requisitos.md#rf-08), [NFR-09](../negocio/requisitos.md#nfr-09)) |

**Índices:**

```sql
-- Filtro por período (RF-02, RF-07)
CREATE INDEX idx_lancamentos_data_competencia
    ON lancamentos(data_competencia);

-- Filtro combinado período + tipo (RF-02)
CREATE INDEX idx_lancamentos_data_tipo
    ON lancamentos(data_competencia, tipo);

-- Resolução de self-join de estornos (RF-08)
CREATE INDEX idx_lancamentos_estorno_de
    ON lancamentos(estorno_de)
    WHERE estorno_de IS NOT NULL;
```

---

### Tabela `outbox`

O Outbox Pattern ([ADR-003](../adr/ADR-003-outbox-pattern.md)) garante que eventos são publicados somente após a persistência confirmada — escrita em `lancamentos` e `outbox` ocorrem na **mesma transação**.

```sql
CREATE TABLE outbox (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo         VARCHAR(100) NOT NULL,
    payload      JSONB       NOT NULL,
    criado_em    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processado_em TIMESTAMPTZ,
    tentativas   SMALLINT    NOT NULL DEFAULT 0
);

-- Relay lê apenas registros não processados (partial index — muito eficiente)
CREATE INDEX idx_outbox_pendentes
    ON outbox(criado_em ASC)
    WHERE processado_em IS NULL;
```

**Ciclo de vida de uma mensagem outbox:**

```mermaid
stateDiagram-v2
    [*] --> Pendente : INSERT (mesma tx do lançamento)
    Pendente --> Publicado : Relay publica no RabbitMQ
    Publicado --> [*] : UPDATE processado_em = NOW()
    Pendente --> Pendente : Retry (tentativas++)
    Pendente --> DLQ_local : tentativas >= MAX_RETRIES
```

O relay faz polling periódico (ex.: a cada 500ms) na `outbox` por registros com `processado_em IS NULL`. Ao publicar com confirmação do broker (`publisher confirm`), atualiza `processado_em`. Mensagens que excedem o limite de tentativas são movidas para análise operacional — nunca descartadas silenciosamente ([NFR-06](../negocio/requisitos.md#nfr-06)).

---

## Serviço de Consolidação — PostgreSQL

### Tabela `lancamentos_processados`

Projeção local dos eventos recebidos. Serve dois propósitos simultâneos: **idempotência** (PK = `id` do evento/lançamento) e **fonte de dados para recálculo** do saldo.

```sql
CREATE TABLE lancamentos_processados (
    id               UUID        PRIMARY KEY,   -- mesmo ID do lançamento original
    tipo             VARCHAR(10) NOT NULL CHECK (tipo IN ('debito', 'credito')),
    valor            NUMERIC(15,2) NOT NULL,
    data_competencia DATE        NOT NULL,
    estorno_de       UUID,                       -- preenchido se for estorno
    estornado_por    UUID,                       -- preenchido pelo Handler B
    processado_em    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lancamentos_proc_data
    ON lancamentos_processados(data_competencia);

CREATE INDEX idx_lancamentos_proc_data_tipo
    ON lancamentos_processados(data_competencia, tipo);
```

**Por que replicar os dados e não consultar o serviço de Lançamentos?**

Porque cruzar fronteiras de serviço para leitura cria acoplamento em tempo de execução — a Consolidação ficaria indisponível se o Lançamentos caísse, violando [NFR-01](../negocio/requisitos.md#nfr-01). A projeção local é o custo certo para manter o isolamento.

---

### Tabela `consolidacao_diaria`

Agregado pré-computado — não é derivado na query, mas recalculado a cada evento processado. Isso garante que a leitura do saldo ([RF-03](../negocio/requisitos.md#rf-03)) seja sempre O(1), independentemente do volume histórico de lançamentos.

```sql
CREATE TABLE consolidacao_diaria (
    data           DATE         PRIMARY KEY,
    total_creditos NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (total_creditos >= 0),
    total_debitos  NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (total_debitos >= 0),
    atualizado_em  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

**Estratégia de upsert idempotente (Handlers A e B):**

```sql
-- Após inserir/atualizar lancamentos_processados, recalcula o agregado:
INSERT INTO consolidacao_diaria (data, total_creditos, total_debitos, atualizado_em)
SELECT
    :data,
    COALESCE(SUM(valor) FILTER (WHERE tipo = 'credito'), 0),
    COALESCE(SUM(valor) FILTER (WHERE tipo = 'debito'),  0),
    NOW()
FROM lancamentos_processados
WHERE data_competencia = :data
ON CONFLICT (data) DO UPDATE SET
    total_creditos = EXCLUDED.total_creditos,
    total_debitos  = EXCLUDED.total_debitos,
    atualizado_em  = EXCLUDED.atualizado_em;
```

O `INSERT ... ON CONFLICT DO UPDATE` é atômico: sem race condition entre dois consumers processando lançamentos do mesmo dia em paralelo — o banco serializa o acesso via lock de linha no `ON CONFLICT`.

---

## Serviço de Consolidação — Redis

### Esquema de chaves

Cache do saldo consolidado com padrão **cache-aside**:

| Chave | Tipo | Valor | TTL |
|-------|------|-------|-----|
| `saldo:{YYYY-MM-DD}` | String (JSON) | `{"total_creditos": 150.00, "total_debitos": 50.00, "saldo": 100.00, "atualizado_em": "..."}` | 60s (dias recentes) / 1h (dias anteriores) |

### Estratégia cache-aside

```mermaid
sequenceDiagram
    participant C as Cliente (Gestor)
    participant A as API Consolidação
    participant R as Redis
    participant P as PostgreSQL

    C->>A: GET /consolidacao/{data}
    A->>R: GET saldo:{data}
    alt Cache hit
        R-->>A: JSON com saldo
        A-->>C: HTTP 200 (cache)
    else Cache miss
        R-->>A: nil
        A->>P: SELECT FROM consolidacao_diaria WHERE data = :data
        P-->>A: linha com totais
        A->>R: SET saldo:{data} {json} EX {ttl}
        A-->>C: HTTP 200 (banco)
    end
```

**Invalidação ativa:** após cada evento processado com sucesso, o consumer invalida a chave Redis correspondente à `data_competencia` do evento (`DEL saldo:{data}`). Isso garante que o próximo read sempre busca o saldo atualizado — sem esperar o TTL expirar.

### Configuração

```yaml
# redis.conf relevante
maxmemory-policy: allkeys-lru   # LRU global se atingir limite de memória
appendonly: yes                 # AOF para durabilidade do cache entre restarts
```

TTL de 60s para dias recentes (podem receber novos lançamentos) e 1h para datas passadas (consolidadas, imutáveis na prática). O TTL atua como safety net — a invalidação ativa é o caminho principal.

---

## Topologia RabbitMQ

### Exchanges e Filas

```mermaid
flowchart LR
    subgraph Lançamentos
        LA["Outbox Relay"]
    end

    subgraph RabbitMQ
        EX["Exchange\nlancamentos.events\n(topic)"]
        Q1["Fila\nconsolidacao.lancamentos"]
        Q2["Fila\nconsolidacao.totais"]
        DLQ1["DLQ\nconsolidacao.lancamentos.dlq"]
        DLQ2["DLQ\nconsolidacao.totais.dlq"]
    end

    subgraph Consolidação
        HA["Handler A\nLancamentoRegistrado"]
        HB["Handler B\nLancamentoEstornado"]
        HC["Handler C\nTotaisDiarioCalculado"]
    end

    LA -->|"lancamento.registrado"| EX
    LA -->|"lancamento.estornado"| EX
    LA -->|"totais.diario.calculado"| EX

    EX -->|"lancamento.*"| Q1
    EX -->|"totais.diario.*"| Q2

    Q1 -->|"DLX após MAX_RETRIES"| DLQ1
    Q2 -->|"DLX após MAX_RETRIES"| DLQ2

    Q1 --> HA
    Q1 --> HB
    Q2 --> HC
```

| Recurso | Configuração | Motivo |
|---------|-------------|--------|
| Exchange `lancamentos.events` | `topic` | Roteamento por routing key — permite adicionar consumidores sem alterar o produtor |
| Fila `consolidacao.lancamentos` | `durable: true` | Sobrevive a restart do broker |
| `x-dead-letter-exchange` | DLX configurado na fila | Mensagens com ACK negativo após `x-delivery-count` máximo vão para DLQ, nunca são descartadas |
| `x-message-ttl` | 30 min | Mensagens presas mais de 30 min provavelmente exigem intervenção operacional |
| `prefetch` | 10 por consumer | Controla throughput e evita que um consumer fique sobrecarregado com mensagens ainda em processamento |

---

## Consistência Eventual — Estratégia Formal

A Consolidação é um **read model** — uma projeção dos eventos do Serviço de Lançamentos. Não existe consistência forte entre os dois, e isso é intencional ([P-03](../negocio/principios.md), [ADR-001](../adr/ADR-001-padrao-arquitetural.md)).

### Janela de inconsistência

```mermaid
timeline
    title Janela de inconsistência após um lançamento
    t+0s : Persistência confirmada
         : Serviço de Lançamentos
    t+1s : Outbox Relay
         : polling 500ms
    t+2s : Publicação no RabbitMQ
    t+3s : Consumer recebe evento
         : Serviço de Consolidação
    t+4s : Upsert + recálculo do saldo
    t+5s : Invalidação do cache Redis
    t+6s : Saldo disponível atualizado
```

Em condições normais, o saldo consolidado fica disponível atualizado dentro de ~2–6 segundos após o registro do lançamento. Em degradação (broker sobrecarregado, consumer lento), a janela se estende mas os dados nunca se perdem — o broker retém os eventos até o consumer processar.

### Garantias oferecidas

| Garantia | Mecanismo |
|----------|-----------|
| Zero perda de lançamentos confirmados | Outbox atômico + at-least-once delivery |
| Idempotência no consumer | PK de `lancamentos_processados` = ID do evento |
| Saldo sempre reconstruível | `lancamentos_processados` + recálculo por `SELECT SUM` |
| DLQ para eventos com falha persistente | `x-dead-letter-exchange` + alerta operacional |
| Detecção de divergência | Reconciliação periódica ([RF-06](../negocio/requisitos.md#rf-06)) |

---

## Migrações de Schema

**Papéis:** 📊 Arquiteto de Dados · 🛠️ Engenheiro de Software

Cada serviço gerencia seu próprio schema com uma ferramenta de migração versionada. As migrações executam automaticamente na inicialização do serviço — sem intervenção manual em deploy.

| Serviço | Ferramenta recomendada | Alternativa |
|---------|----------------------|-------------|
| Serviço de Lançamentos | **Flyway** (Java/Kotlin) ou **Alembic** (Python) | Liquibase |
| Serviço de Consolidação | Mesma escolha da linguagem da Etapa 7 | — |

**Convenção de nomenclatura dos arquivos:**

```
migrations/
  V1__create_lancamentos.sql
  V2__create_outbox.sql
  V3__add_index_data_tipo.sql
```

**Regras invioláveis:**

1. **Nunca alterar uma migration já aplicada** — cada arquivo é imutável após merge na branch principal
2. **Toda alteração de schema é uma nova migration** — mesmo correção de typo em `CHECK` constraint
3. **Migrations devem ser retrocompatíveis com a versão anterior do código** durante o janela de deploy — adicionar coluna com `DEFAULT`, nunca renomear ou remover sem ciclo de deprecação

**Estratégia para colunas `NOT NULL` em tabelas com dados:**

```sql
-- Passo 1 (migration V_n): adicionar como nullable
ALTER TABLE lancamentos ADD COLUMN canal VARCHAR(50);

-- Passo 2: preencher valores existentes (em migration ou job separado)
UPDATE lancamentos SET canal = 'legado' WHERE canal IS NULL;

-- Passo 3 (migration V_n+1, após deploy estável): aplicar constraint
ALTER TABLE lancamentos ALTER COLUMN canal SET NOT NULL;
```

Essa sequência permite deploy sem downtime — o código novo escreve o campo, o código antigo ignora, e a constraint só entra quando todos os registros foram preenchidos.

---

## Limpeza da Tabela `outbox`

**Papéis:** 📊 Arquiteto de Dados · 👁️ Arquiteto de Observabilidade

A tabela `outbox` é append-only por natureza: o relay marca registros como processados (`processado_em IS NOT NULL`) mas nunca os apaga. Sem limpeza, a tabela cresce indefinidamente — degradando o índice partial `WHERE processado_em IS NULL` e consumindo espaço desnecessário.

**Job de arquivamento (executar diariamente):**

```sql
-- Remove mensagens processadas há mais de 7 dias
-- Executar em lotes para não bloquear a tabela por longos períodos
DELETE FROM outbox
WHERE processado_em IS NOT NULL
  AND processado_em < NOW() - INTERVAL '7 days'
  AND id IN (
    SELECT id FROM outbox
    WHERE processado_em IS NOT NULL
      AND processado_em < NOW() - INTERVAL '7 days'
    LIMIT 1000
  );
```

| Parâmetro | Valor | Motivo |
|-----------|-------|--------|
| Retenção de processados | 7 dias | Janela de auditoria operacional — permite investigar falhas recentes |
| Tamanho do lote | 1.000 registros | Evita lock prolongado da tabela durante produção |
| Frequência | Diária (madrugada) | Baixo tráfego; reduz contenção com o relay |

**Alternativa para volumes altos:** particionamento da `outbox` por `criado_em` (RANGE mensal), permitindo `DROP PARTITION` em vez de `DELETE` — operação instantânea e sem bloqueio.

---

## Capacidade e Crescimento

**Papéis:** 📊 Arquiteto de Dados · 🏗️ Arquiteto de Infraestrutura

Estimativa de crescimento baseada no perfil de um comerciante de médio porte com operação diária.

**Premissas:**

| Parâmetro | Estimativa | Base |
|-----------|-----------|------|
| Lançamentos por dia | ~500 | Comerciante com fluxo moderado |
| Tamanho médio por linha (`lancamentos`) | ~200 bytes | UUID + campos fixos + descrição média |
| Retenção fiscal obrigatória | 5 anos | Lei 9.613/98 + Receita Federal |

**Projeção de crescimento:**

| Horizonte | Registros (`lancamentos`) | Espaço estimado |
|-----------|--------------------------|----------------|
| 1 mês | ~15.000 | ~3 MB |
| 1 ano | ~182.500 | ~37 MB |
| 5 anos | ~912.500 | ~183 MB |

A tabela `lancamentos` permanece pequena mesmo em 5 anos para este perfil. **Particionamento não é necessário** para o volume especificado — fica como evolução natural se o comerciante crescer para volumes 10× maiores (>5.000 lançamentos/dia).

A tabela `lancamentos_processados` cresce na mesma proporção. A `consolidacao_diaria` tem exatamente **1 linha por dia** — 365 linhas/ano, trivialmente pequena.

**Sinal para reavaliar particionamento:** query `SELECT SUM(valor) FROM lancamentos_processados WHERE data_competencia = :data` ultrapassar 10ms de latência — indício de que o índice `idx_lancamentos_proc_data` não é mais eficiente o suficiente.

**Estimativa de storage PostgreSQL total (5 anos):**

| Tabela | Tamanho estimado |
|--------|----------------|
| `lancamentos` | ~183 MB |
| `outbox` (com limpeza semanal) | ~5 MB (estável) |
| `lancamentos_processados` | ~183 MB |
| `consolidacao_diaria` | < 1 MB |
| Índices (estimativa 40% das tabelas) | ~150 MB |
| **Total** | **~522 MB** |

Bem dentro do `db.t3.medium` (20 GB gp3) provisionado no Terraform — há espaço para crescimento de 40× antes de precisar aumentar o volume.

---

## Privacidade e LGPD

**Papéis:** 📊 Arquiteto de Dados · 🔒 Arquiteto de Segurança

### Classificação dos dados por tabela

| Tabela | Dados pessoais? | Justificativa |
|--------|----------------|---------------|
| `lancamentos` | **Não** (por design) | Armazena tipo, valor, data e descrição — dados transacionais sem identificação de pessoa natural |
| `outbox` | **Não** | Payload dos eventos — espelho dos campos de `lancamentos` |
| `lancamentos_processados` | **Não** | Projeção dos mesmos dados financeiros |
| `consolidacao_diaria` | **Não** | Totais agregados por dia — impossível identificar indivíduo |
| Redis (`saldo:{data}`) | **Não** | Apenas valores numéricos agregados |

O schema financeiro adota por design o **princípio da minimização** (LGPD art. 6º, V): nenhum dado de identificação pessoal é coletado nas tabelas de negócio.

### Vetores de risco

**1. Campo `descricao` — risco indireto**

O campo é livre e um operador descuidado pode inserir dados pessoais (`"Venda CPF 123.456.789-00"`, `"Serviço João Silva"`). Mitigações:

- Documentar no manual operacional que `descricao` é campo de natureza do lançamento, não de identificação do cliente
- Validação no frontend: bloquear padrões de CPF/CNPJ com regex antes do envio
- Validação no backend: `RF-05` pode incluir rejeição de payloads que contenham padrões de CPF/CNPJ

**2. Trilha de auditoria (NFR-09) — risco planejado**

A implementação de auditoria na Etapa 7 registrará *quem* criou cada lançamento — o `sub` (subject) do JWT, que identifica o Caixa/Gestor. **Isso é dado pessoal** de uma pessoa natural.

| Artefato de auditoria | Onde fica | Contém dado pessoal |
|----------------------|-----------|---------------------|
| Log estruturado de cada requisição | CloudWatch Logs | Sim — `user_id`, IP |
| Tabela `audit_log` (Etapa 7) | PostgreSQL (Lançamentos) | Sim — `operador_id`, timestamp |

**3. Serviço de Autenticação (Etapa 7)**

Nome, e-mail e possivelmente CPF do Gestor e do Caixa. Completamente fora do escopo da Etapa 4 — documentado aqui para rastreabilidade do risco.

### Política de retenção

| Dado | Retenção | Base legal |
|------|----------|-----------|
| Registros financeiros (`lancamentos`, `consolidacao_diaria`) | **5 anos** | Obrigação fiscal — Lei 9.613/98, IN RFB 1.990/2020 |
| Logs de auditoria (`audit_log`, CloudWatch) | **2 anos** | LGPD art. 16 — dados de legítimo interesse com prazo razoável |
| Cache Redis (`saldo:{data}`) | TTL 60s–1h | Dado derivado, não retido |
| Outbox processada | **7 dias** | Janela operacional — sem obrigação legal após processamento |

### Direito de exclusão (LGPD art. 18)

Os dados financeiros em `lancamentos` **não podem ser apagados** durante o período de retenção fiscal (5 anos) — a obrigação legal prevalece sobre o direito de exclusão (LGPD art. 16, II). Após o período obrigatório, a exclusão é viável porque os registros não contêm PII estruturado.

Para os logs de auditoria (que contêm `operador_id`): o direito de exclusão se aplica após 2 anos, com anonimização como alternativa ao apagamento — substituir `operador_id` por hash irreversível preserva a integridade do log sem identificar a pessoa.

---

## ABB → SBB — Mapeamento de Blocos Arquiteturais

| ABB (conceito arquitetural) | SBB (implementação concreta) |
|-----------------------------|------------------------------|
| Repositório de eventos de lançamento | Tabela `lancamentos` + índices em `data_competencia` |
| Mecanismo de entrega confiável de eventos | Tabela `outbox` + Outbox Relay (polling 500ms) |
| Broker de mensagens | RabbitMQ — exchange `lancamentos.events` (topic) |
| Dead Letter Queue | `consolidacao.lancamentos.dlq` via `x-dead-letter-exchange` |
| Read model da consolidação | Tabela `lancamentos_processados` (projeção local por serviço) |
| Agregado consolidado | Tabela `consolidacao_diaria` (upsert pré-computado) |
| Cache de leitura de alta performance | Redis — chave `saldo:{YYYY-MM-DD}`, TTL 60s/1h, invalidação ativa |
| Idempotência de eventos | PK de `lancamentos_processados` = UUID do lançamento original |
