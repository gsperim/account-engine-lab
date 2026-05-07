---
tags:
  - engenharia
  - integracao
  - contratos
---

# Contratos de Integração

**Papéis:** 🔗 Arquiteto de Integração · 🛠️ Engenheiro de Software
**Framework:** C4 — Nível de Código (contratos entre containers)

Esta página define os contratos formais de integração do sistema: eventos de domínio que trafegam pelo broker e a estrutura das APIs REST expostas pelo API Gateway. Qualquer alteração nesses contratos exige versionamento explícito.

---

## Eventos de Domínio

Todos os eventos seguem o envelope padrão abaixo. O campo `payload` varia por tipo de evento.

### Envelope padrão

```json
{
  "id":           "uuid-v4",
  "tipo":         "NomeDoEvento",
  "versao":       "1.0",
  "criado_em":    "2026-05-07T14:30:00Z",
  "payload":      { }
}
```

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | uuid | Identificador único do evento — usado para idempotência no consumidor |
| `tipo` | string | Nome do tipo de evento |
| `versao` | string | Versão do schema do payload — permite evolução sem quebrar consumidores |
| `criado_em` | datetime | Timestamp UTC de criação do evento |
| `payload` | object | Dados específicos do tipo de evento |

---

### `LancamentoRegistrado`

**Produtor:** Serviço de Lançamentos
**Consumidor:** Serviço de Consolidação Diária (Handler A — [RF-04](../negocio/requisitos.md#rf-04))
**Trigger:** Persistência confirmada de um novo lançamento ([RF-01](../negocio/requisitos.md#rf-01))

```json
{
  "id":        "550e8400-e29b-41d4-a716-446655440000",
  "tipo":      "LancamentoRegistrado",
  "versao":    "1.0",
  "criado_em": "2026-05-07T14:30:00Z",
  "payload": {
    "id":               "7f3b9a10-1c2d-4e5f-8a9b-0c1d2e3f4a5b",
    "tipo":             "credito",
    "valor":            150.00,
    "data_competencia": "2026-05-07",
    "descricao":        "Venda balcão",
    "criado_em":        "2026-05-07T14:29:59Z"
  }
}
```

| Campo do payload | Tipo | Obrigatório | Descrição |
|-----------------|------|-------------|-----------|
| `id` | uuid | Sim | ID do lançamento — chave de idempotência no consumidor |
| `tipo` | enum | Sim | `debito` ou `credito` |
| `valor` | decimal | Sim | Valor positivo com até duas casas decimais |
| `data_competencia` | date | Sim | Data financeira do lançamento (ISO 8601) |
| `descricao` | string | Sim | Descrição do lançamento |
| `criado_em` | datetime | Sim | Timestamp UTC de criação |

---

### `LancamentoEstornado`

**Produtor:** Serviço de Lançamentos
**Consumidor:** Serviço de Consolidação Diária (Handler B — [RF-04](../negocio/requisitos.md#rf-04))
**Trigger:** Persistência confirmada de um estorno ([RF-08](../negocio/requisitos.md#rf-08))

```json
{
  "id":        "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "tipo":      "LancamentoEstornado",
  "versao":    "1.0",
  "criado_em": "2026-05-07T15:00:00Z",
  "payload": {
    "id":                    "9c8b7a60-5d4e-3f2a-1b0c-9d8e7f6a5b4c",
    "tipo":                  "debito",
    "valor":                 150.00,
    "data_competencia":      "2026-05-07",
    "descricao":             "Estorno: Venda balcão",
    "estorno_de":            "7f3b9a10-1c2d-4e5f-8a9b-0c1d2e3f4a5b",
    "motivo":                "Pagamento cancelado pelo cliente",
    "criado_em":             "2026-05-07T14:59:58Z"
  }
}
```

| Campo do payload | Tipo | Obrigatório | Descrição |
|-----------------|------|-------------|-----------|
| `id` | uuid | Sim | ID do estorno — chave de idempotência no consumidor |
| `tipo` | enum | Sim | Tipo inverso ao original: `credito` vira `debito` e vice-versa |
| `valor` | decimal | Sim | Mesmo valor do lançamento original |
| `data_competencia` | date | Sim | Mesma data de competência do original |
| `descricao` | string | Sim | Descrição gerada automaticamente |
| `estorno_de` | uuid | Sim | ID do lançamento original — vínculo de rastreabilidade |
| `motivo` | string | Sim | Motivo do estorno informado pelo operador |
| `criado_em` | datetime | Sim | Timestamp UTC do estorno |

---

### `TotaisDiarioCalculado`

**Produtor:** Serviço de Lançamentos
**Consumidor:** Serviço de Consolidação Diária
**Trigger:** Execução de recálculo assíncrono ([RF-07](../negocio/requisitos.md#rf-07))

```json
{
  "id":        "f0e1d2c3-b4a5-6789-0fed-cba987654321",
  "tipo":      "TotaisDiarioCalculado",
  "versao":    "1.0",
  "criado_em": "2026-05-07T16:00:00Z",
  "payload": {
    "data":             "2026-05-06",
    "total_creditos":   3200.00,
    "total_debitos":    1750.50,
    "job_id":           "c3d4e5f6-a7b8-9012-cdef-345678901234"
  }
}
```

| Campo do payload | Tipo | Obrigatório | Descrição |
|-----------------|------|-------------|-----------|
| `data` | date | Sim | Data de competência calculada (ISO 8601) |
| `total_creditos` | decimal | Sim | Soma de todos os créditos da data |
| `total_debitos` | decimal | Sim | Soma de todos os débitos da data |
| `job_id` | uuid | Sim | ID da solicitação de recálculo — chave de idempotência |

---

## Versionamento de Contratos

Adições de campos opcionais não quebram consumidores e não exigem bump de versão. Remoções, renomeações ou mudanças de tipo exigem nova versão (`"versao": "2.0"`) com período de coexistência até todos os consumidores migrarem.

O campo `versao` no envelope permite que consumidores detectem e rejeitem explicitamente versões não suportadas.

---

## APIs REST

Os contratos detalhados de cada endpoint (campos de entrada, saída, códigos HTTP e critérios de aceite) estão documentados nos Requisitos Funcionais:

| Endpoint | Método | RF | Serviço |
|----------|--------|----|---------|
| `/lancamentos` | POST | [RF-01](../negocio/requisitos.md#rf-01) | Lançamentos |
| `/lancamentos` | GET | [RF-02](../negocio/requisitos.md#rf-02) | Lançamentos |
| `/lancamentos/{id}/estorno` | POST | [RF-08](../negocio/requisitos.md#rf-08) | Lançamentos |
| `/lancamentos/recalcular` | POST | [RF-07](../negocio/requisitos.md#rf-07) | Lançamentos |
| `/consolidacao/{data}` | GET | [RF-03](../negocio/requisitos.md#rf-03) | Consolidação |
| `/consolidacao/reconciliacao` | POST | [RF-06](../negocio/requisitos.md#rf-06) | Consolidação |
| `/consolidacao/periodo` | GET | [RF-09](../negocio/requisitos.md#rf-09) | Consolidação |

> O contrato formal em OpenAPI (Swagger) será gerado na Etapa 7 — Implementação, quando os endpoints estiverem codificados.
