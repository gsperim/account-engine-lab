---
tags:
  - adr
  - decisao
  - arquitetura
---

# ADR-001 — Padrão Arquitetural: Microserviços Orientados a Eventos

**Papéis:** 🧩 Arquiteto de Soluções · 🔗 Arquiteto de Integração
**Data:** 2026-05-07
**Status:** Aceito
**Requisitos:** [NFR-01](../negocio/requisitos.md#nfr-01), [NFR-03](../negocio/requisitos.md#nfr-03), [D-03](../negocio/drivers.md#d-03)

---

## Contexto

O sistema precisa atender dois requisitos em tensão direta:

1. **Disponibilidade do registro** — o Serviço de Lançamentos não pode ficar indisponível se o Serviço de Consolidação Diária cair (NFR-01). Qualquer acoplamento síncrono direto entre os dois cria uma dependência que viola este requisito.
2. **Consistência dos saldos** — o consolidado precisa refletir todos os lançamentos, sem perda, mesmo em cenários de falha parcial (NFR-03).

Esses dois requisitos juntos eliminam qualquer arquitetura onde os serviços se comunicam de forma síncrona: uma chamada REST direta do Lançamentos para a Consolidação criaria a dependência que NFR-01 proíbe.

---

## Alternativas Consideradas

| Opção | Por que foi descartada |
|-------|----------------------|
| **Monolito** | Não separa as responsabilidades; uma falha no módulo de consolidação afetaria o registro de lançamentos |
| **Microserviços com chamadas REST síncronas** | O Lançamentos chamaria a Consolidação diretamente — viola NFR-01: se a Consolidação cair, o Lançamentos aguardaria resposta ou falharia |
| **Arquitetura serverless** | Complexidade operacional desnecessária para o volume especificado; testabilidade local reduzida |

---

## Decisão

Adotar **microserviços orientados a eventos com comunicação assíncrona via broker de mensagens**.

Após cada persistência confirmada, o Serviço de Lançamentos publica o evento `LancamentoRegistrado` em um broker. O Serviço de Consolidação Diária consome esse evento de forma independente — sem que o Lançamentos saiba se, quando ou quantas vezes o evento foi processado.

O diagrama de containers (C4 L2) reflete essa topologia: os dois serviços nunca se comunicam diretamente; toda integração passa pelo broker.

---

## Consequências

### Positivas

- **NFR-01 garantido por design** — a indisponibilidade da Consolidação não bloqueia o Lançamentos; o broker absorve os eventos até o serviço se recuperar
- **Escalabilidade independente** — cada serviço escala conforme sua própria carga
- **Deploy independente** — mudanças em um serviço não forçam redeploy do outro
- **Base para resiliência** — at-least-once delivery, DLQ e retry são naturais neste modelo

### Negativas / Trade-offs

- **Consistência eventual** — o saldo consolidado pode estar momentaneamente desatualizado após um lançamento. Declarado explicitamente em [P-03](../negocio/principios.md#p-03) como trade-off aceito.
- **Complexidade operacional** — um broker de mensagens precisa ser provisionado, monitorado e mantido
- **Debugging distribuído** — rastrear o caminho de um lançamento até o saldo exige correlação de logs entre serviços (mitigado por NFR-04 e NFR-09)
- **Idempotência obrigatória no consumidor** — o Serviço de Consolidação Diária deve tratar reentregas sem duplicar valores (endereçado em [RF-04](../negocio/requisitos.md#rf-04))
