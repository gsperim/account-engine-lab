---
tags:
  - adr
  - decisao
  - confiabilidade
---

# ADR-003 — Garantia de Entrega: Transactional Outbox Pattern

**Papéis:** 🧩 Arquiteto de Soluções · 🛠️ Engenheiro de Software
**Data:** 2026-05-07
**Status:** Aceito
**Requisitos:** [NFR-03](../negocio/requisitos.md#nfr-03), [RF-01](../negocio/requisitos.md#rf-01), [D-05](../negocio/drivers.md#d-05)

---

## Contexto

[RF-01](../negocio/requisitos.md#rf-01) define que o evento `LancamentoRegistrado` deve ser publicado **somente após** a persistência confirmada do lançamento. Isso cria um problema clássico de sistemas distribuídos: o **dual-write problem**.

Se o Serviço de Lançamentos escrever no banco e depois publicar no broker em operações separadas, dois cenários de falha surgem:

1. **Falha entre o write e o publish** — o lançamento existe no banco mas o evento nunca chega à Consolidação; o saldo fica desatualizado permanentemente
2. **Publicação sem persistência** — o broker recebe o evento mas o lançamento foi revertido; a Consolidação processa um lançamento fantasma

Nenhuma das abordagens ingênuas resolve isso sem comprometer a disponibilidade:

- **Two-phase commit (2PC)** distribui a transação entre banco e broker, mas reduz disponibilidade e aumenta latência — viola o espírito de [NFR-01](../negocio/requisitos.md#nfr-01)
- **Publicar e torcer** não oferece garantia alguma

---

## Alternativas Consideradas

| Opção | Por que foi descartada |
|-------|----------------------|
| **Two-phase commit (2PC)** | Acopla banco e broker em uma transação distribuída; latência elevada; reduz disponibilidade de ambos os recursos |
| **Publicar antes de persistir** | Cria lançamentos fantasmas no consolidado se a persistência falhar |
| **Polling do banco pela Consolidação** | Cria acoplamento direto ao banco do Lançamentos — viola [P-04](../negocio/principios.md#p-04) (Database per Service) e [P-01](../negocio/principios.md#p-01) (Desacoplamento) |
| **Event Sourcing** | Elimina o problema por design, mas exige reescrita completa do modelo de persistência; complexidade desproporcional ao escopo atual |

---

## Decisão

Adotar o **Transactional Outbox Pattern**.

O Serviço de Lançamentos escreve o lançamento e o evento pendente em uma mesma transação de banco de dados:

```sql
BEGIN;

INSERT INTO lancamentos (id, tipo, valor, data_competencia, descricao, criado_em)
VALUES (:id, :tipo, :valor, :data, :descricao, NOW());

INSERT INTO outbox (id, evento_tipo, payload, criado_em, publicado)
VALUES (:id, 'LancamentoRegistrado', :payload_json, NOW(), false);

COMMIT;
```

Um componente dedicado — o **Outbox Relay** — executa em background e publica os eventos pendentes no RabbitMQ, marcando-os como publicados após o ACK do broker:

```sql
-- Relay lê eventos não publicados
SELECT * FROM outbox WHERE publicado = false ORDER BY criado_em LIMIT 100;

-- Após ACK do broker
UPDATE outbox SET publicado = true, publicado_em = NOW() WHERE id = :id;
```

Se o Relay cair entre a publicação e o UPDATE, o evento é republicado na próxima execução — resultando em **at-least-once delivery**. O Serviço de Consolidação Diária já é idempotente ([RF-04](../negocio/requisitos.md#rf-04)), então reentregas são seguras.

---

## Consequências

### Positivas

- **[NFR-03](../negocio/requisitos.md#nfr-03) garantido** — o lançamento e o evento são atomicamente persistidos; não há janela de falha entre os dois
- **Alta disponibilidade** — o Relay é stateless e reiniciável; falhas temporárias não resultam em perda de eventos
- **Sem dependência transacional do broker** — o banco de dados (sempre disponível) é o buffer primário; o broker é notificado de forma assíncrona
- **Auditoria natural** — a tabela `outbox` registra todos os eventos publicados com timestamp (contribui para NFR-09)

### Negativas / Trade-offs

- **Latência adicional** — há um delay entre o commit do lançamento e a chegada do evento à Consolidação (depende do intervalo de polling do Relay); aceitável dado que [NFR-01](../negocio/requisitos.md#nfr-01) e [P-03](../negocio/principios.md#p-03) já estabelecem consistência eventual
- **Componente adicional** — o Outbox Relay precisa ser implementado, deployado e monitorado como parte do Serviço de Lançamentos
- **Crescimento da tabela outbox** — eventos publicados devem ser purgados periodicamente para evitar crescimento ilimitado; estratégia de retenção necessária (relacionado a [C-04](../negocio/requisitos.md#c-04))
- **At-least-once delivery obrigatório** — o consumidor deve ser idempotente; já garantido por [RF-04](../negocio/requisitos.md#rf-04)
