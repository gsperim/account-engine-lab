---
tags:
  - adr
  - decisao
  - dados
  - persistencia
---

# ADR-012 — Modelagem de Dados e Mecanismos de Persistência

**Papéis:** 📊 Arquiteto de Dados · 🔗 Arquiteto de Integração  
**Data:** 2026-05-09  
**Status:** Aceito  
**Requisitos:** [RF-01](../negocio/requisitos.md#rf-01), [RF-03](../negocio/requisitos.md#rf-03), [RF-04](../negocio/requisitos.md#rf-04), [NFR-03](../negocio/requisitos.md#nfr-03), [NFR-09](../negocio/requisitos.md#nfr-09)

---

## Contexto

A modelagem de dados precisa atender simultaneamente:

1. **Precisão financeira** — valores monetários não podem acumular erro de ponto flutuante. Um `FLOAT` em operações repetidas de soma produz resultados como `150.00000000001`, inaceitável em sistemas financeiros.
2. **Idempotência de eventos** — o Serviço de Consolidação consome eventos `at-least-once`: o mesmo evento pode chegar duas vezes. A estrutura do banco precisa absorver reentregas sem duplicar valores.
3. **Leitura de saldo em O(1)** — o consolidado precisa responder a 50 req/s ([NFR-02](../negocio/requisitos.md#nfr-02)) com latência baixa. Não é viável recalcular `SUM(valor)` sobre todos os lançamentos históricos a cada requisição.
4. **Trilha de auditoria imutável** — toda operação financeira deve ser rastreável ([NFR-09](../negocio/requisitos.md#nfr-09)). Lançamentos não podem ser editados ou excluídos.
5. **Entrega confiável de eventos** — a publicação no broker deve ocorrer somente após a persistência confirmada ([ADR-003](ADR-003-outbox-pattern.md)).

---

## Decisão

### 1. `NUMERIC(15,2)` para valores monetários

Adotar `NUMERIC(15,2)` (precisão exata) em vez de `FLOAT`/`DOUBLE PRECISION` para todos os campos de valor monetário — `valor` em `lancamentos`, `total_creditos` e `total_debitos` em `consolidacao_diaria`.

`NUMERIC` armazena cada dígito decimal com fidelidade absoluta. `FLOAT` é uma representação binária de base 2 que não consegue expressar a maioria das frações decimais de forma exata. Em um sistema financeiro, o erro acumulado é inadmissível.

**Por que `(15,2)` e não `(10,2)` ou `(20,2)`:** 15 dígitos no total com 2 casas decimais suporta valores até R$ 9.999.999.999.999,99 — mais do que suficiente para um comerciante de qualquer porte; menos do que `(20,2)`, que desperdiçaria espaço em storage e índices.

### 2. UUID como chave primária

Adotar `UUID` como chave primária em `lancamentos` (gerado via `gen_random_uuid()`) e como chave de idempotência em `lancamentos_processados` (mesmo UUID do lançamento original).

**Alternativa descartada:** `BIGSERIAL` (auto-increment). Sequências inteiras vazam informação de volume (o cliente consegue inferir quantos registros existem pelo ID devolvido) e não são portáveis entre ambientes de teste e produção sem conflito de sequências.

**Por que `gen_random_uuid()` e não gerar na aplicação:** o banco garante unicidade sem round-trip. A aplicação pode gerar o UUID antes do INSERT para incluir no payload de resposta HTTP — ambos os padrões são compatíveis; o banco serve como rede de segurança.

### 3. Lançamentos imutáveis (append-only)

A tabela `lancamentos` é append-only: não há `UPDATE` nem `DELETE` sobre registros confirmados. Correções são feitas via estorno ([RF-08](../negocio/requisitos.md#rf-08)) — um novo lançamento com tipo inverso, `estorno_de` apontando para o original.

Isso garante a trilha de auditoria completa ([NFR-09](../negocio/requisitos.md#nfr-09)): cada operação financeira é um registro permanente, nunca sobrescrito. O campo `estornado_por` é o único campo que pode ser preenchido após a criação (pelo Handler B).

### 4. Outbox por polling (não por trigger de banco)

O relay do Outbox ([ADR-003](ADR-003-outbox-pattern.md)) faz **polling** periódico na tabela `outbox` em vez de usar triggers de banco (`NOTIFY`/`LISTEN` no PostgreSQL) ou Change Data Capture (CDC) via WAL.

| Abordagem | Vantagem | Desvantagem |
|-----------|----------|------------|
| **Polling** (escolhida) | Simples, portável, sem dependência de feature do banco | Latência mínima = intervalo de polling (500ms) |
| Trigger + `NOTIFY`/`LISTEN` | Menor latência (milissegundos) | Acoplamento ao PostgreSQL; conexão persistente no relay; mais difícil de testar |
| CDC via WAL (Debezium) | Zero acoplamento, stream contínuo | Complexidade operacional alta demais para o volume (50 req/s) |

A latência adicional de 500ms é irrelevante para consistência eventual — o SLA de atualização do consolidado já é dado em segundos, não milissegundos.

### 5. `lancamentos_processados` como read model com idempotência nativa

A tabela `lancamentos_processados` no Serviço de Consolidação serve dois propósitos simultâneos:

- **Idempotência:** PK = `id` do lançamento original. `INSERT ... ON CONFLICT DO NOTHING` absorve reentregas sem duplicar dados.
- **Fonte canônica local:** base para o recálculo do saldo via `SELECT SUM`. Evita consulta cross-service ao Serviço de Lançamentos.

**Alternativa descartada:** usar apenas a tabela `consolidacao_diaria` e atualizar o saldo com `+= valor` (incremento). O incremento parece mais eficiente, mas é não-idempotente: processar o mesmo evento duas vezes duplicaria o valor. A abordagem de recálculo completo (`SELECT SUM FROM lancamentos_processados`) é idempotente por natureza — processar o mesmo evento duas vezes não muda o resultado do SUM.

### 6. `consolidacao_diaria` como agregado pré-computado

O saldo do dia é calculado e armazenado a cada evento processado, em vez de ser calculado na query de leitura.

**Por que pré-computar:** com `SELECT SUM(valor) FROM lancamentos_processados WHERE data = :data` executado a cada `GET /consolidacao/{data}` e 50 req/s, o banco receberia 50 queries de agregação por segundo sobre uma tabela que cresce indefinidamente. Com pré-computação, a query de leitura é `SELECT ... FROM consolidacao_diaria WHERE data = :data` — um lookup de chave primária, O(1) independentemente do histórico.

**Consistência do pré-computo:** o upsert usa `INSERT ... ON CONFLICT DO UPDATE` com o `SUM` calculado na mesma query. Isso é atômico no PostgreSQL — sem race condition entre dois consumers processando lançamentos do mesmo dia em paralelo.

### 7. Redis cache-aside com invalidação ativa

Cache do saldo consolidado com TTL duplo: 60 segundos para datas recentes (podem receber novos lançamentos hoje), 1 hora para datas passadas. Além do TTL, o consumer invalida ativamente a chave após cada evento processado.

**Alternativa descartada:** cache write-through (atualizar o cache no mesmo momento que o banco). Write-through exigiria que o consumer tivesse acesso de escrita ao Redis — correto arquiteturalmente, mas cria risco de inconsistência se o consumer falhar entre o write no banco e o write no cache. Com cache-aside + invalidação, o pior caso é uma cache miss, nunca um cache stale não expirado.

---

## Alternativas Consideradas

### MongoDB no lugar de PostgreSQL

MongoDB é frequentemente sugerido para microserviços por sua flexibilidade de schema. Descartado porque:

- Os schemas são **relacionais e estruturados** — não há requisito de flexibilidade de schema
- `NUMERIC` com precisão exata não existe no MongoDB nativo — o tipo `Decimal128` existe, mas tem menos suporte em ORMs
- O Outbox Pattern é mais simples em PostgreSQL (transação ACID nativa)
- `ON CONFLICT DO NOTHING` / `ON CONFLICT DO UPDATE` (upsert) é mais expressivo e performático que o equivalente em MongoDB

### DynamoDB no lugar de PostgreSQL

Descartado. DynamoDB exige design cuidadoso de partition key para evitar hot partitions. Para `consolidacao_diaria`, a partition key natural seria `data` — todas as requisições de hoje iriam para a mesma partição. Requer design alternativo que complica o modelo sem benefício justificável para o volume de 50 req/s.

### Memcached no lugar de Redis

Descartado. Memcached não suporta persistência (AOF/RDB) — um restart apaga o cache inteiro, gerando spike de cache miss. Redis com AOF sobrevive a restarts com o cache quase integralmente preservado.

---

## Consequências

### Positivas

- Precisão financeira garantida por tipo de dado — não depende de disciplina do desenvolvedor
- Idempotência do consumer garantida por constraint de banco — não depende de lógica de aplicação
- Saldo consolidado em O(1) — latência de leitura independe do volume histórico
- Trilha de auditoria completa — lançamentos são imutáveis por design de schema
- Outbox portável — não depende de features específicas do PostgreSQL

### Negativas / Trade-offs

- **Duplicação de dados:** `lancamentos_processados` replica parte do estado de `lancamentos`. Custo: storage adicional e lógica de projeção. Benefício: isolamento de serviço e idempotência garantida.
- **Recálculo full-scan por data no consumer:** `SELECT SUM FROM lancamentos_processados WHERE data = :data` faz um scan parcial a cada evento. Para volumes muito altos (milhares de lançamentos por dia), esse scan cresce — mitigado pelo índice em `data_competencia`.
- **Latência do polling:** o Outbox Relay adiciona até 500ms de latência entre persistência e publicação. Aceitável para consistência eventual — não para casos de uso que exigem propagação em tempo real.
