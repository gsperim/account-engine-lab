---
tags:
  - adr
  - decisao
  - infraestrutura
---

# ADR-002 — Message Broker: RabbitMQ

**Papéis:** ⚙️ Arquiteto de Tecnologia · 🏗️ Arquiteto de Infraestrutura
**Data:** 2026-05-07
**Status:** Aceito
**Requisitos:** [NFR-02](../negocio/requisitos.md#nfr-02), [NFR-03](../negocio/requisitos.md#nfr-03), [NFR-06](../negocio/requisitos.md#nfr-06), [NFR-08](../negocio/requisitos.md#nfr-08), [RF-07](../negocio/requisitos.md#rf-07)

---

## Contexto

A decisão [ADR-001](ADR-001-padrao-arquitetural.md) exige um broker de mensagens. As opções principais diferem em throughput, modelo de retenção, complexidade operacional e adequação ao volume especificado (50 req/s, [NFR-02](../negocio/requisitos.md#nfr-02)).

O broker precisa atender:

- **At-least-once delivery** com filas duráveis ([NFR-03](../negocio/requisitos.md#nfr-03))
- **Dead Letter Queue** para eventos que esgotam retentativas ([NFR-06](../negocio/requisitos.md#nfr-06))
- **Retry com backoff** ([NFR-08](../negocio/requisitos.md#nfr-08))
- **50 req/s** no pico de carga ([NFR-02](../negocio/requisitos.md#nfr-02))

Um requisito que influenciou a análise foi o [RF-07](../negocio/requisitos.md#rf-07) (recálculo assíncrono): a estratégia de recovery da Consolidação usa o endpoint [RF-02](../negocio/requisitos.md#rf-02) do Serviço de Lançamentos, **não** retenção de mensagens no broker. Isso remove a necessidade de retenção longa como critério de seleção.

---

## Alternativas Consideradas

| Opção | Vantagens | Razão do descarte |
|-------|-----------|-------------------|
| **Apache Kafka** | Throughput massivo, retenção nativa de eventos, ideal para event sourcing | Requer ZooKeeper ou KRaft (complexidade operacional); retenção não é necessária ([RF-07](../negocio/requisitos.md#rf-07) usa [RF-02](../negocio/requisitos.md#rf-02)); overhead justificado apenas acima de milhares de req/s |
| **AWS SQS + SNS** | Gerenciado, sem ops | Acoplamento a cloud provider; inviabiliza execução local puramente via `docker compose` ([C-01](../negocio/requisitos.md#c-01)) |
| **Redis Streams** | Baixa latência, simples | Menor ecossistema de ferramentas para DLQ e retry; persistência limitada comparada a brokers dedicados |

---

## Decisão

Adotar **RabbitMQ** como broker de mensagens.

Justificativas:

- **Throughput suficiente** — RabbitMQ suporta dezenas de milhares de mensagens/segundo; 50 req/s está muito abaixo do limite prático
- **DLQ nativa** — Dead Letter Exchange (DLX) implementa [NFR-06](../negocio/requisitos.md#nfr-06) sem configuração adicional; mensagens rejeitadas após N tentativas são roteadas automaticamente para uma fila de análise
- **Retry com backoff** — combinação de TTL por mensagem + DLX implementa [NFR-08](../negocio/requisitos.md#nfr-08); a mensagem expira, cai na dead letter queue de staging e é reenfileirada com delay crescente
- **Operação simples em docker-compose** — imagem oficial `rabbitmq:management`; interface de administração em http://localhost:15672; sem dependências externas ([C-01](../negocio/requisitos.md#c-01))
- **Recovery via RF-02** — a decisão de usar [RF-02](../negocio/requisitos.md#rf-02) para reconstrução da Consolidação ([RF-07](../negocio/requisitos.md#rf-07)) torna a retenção longa do Kafka dispensável
- **Modelo AMQP** — exchanges e bindings permitem rotear `LancamentoRegistrado` para múltiplos consumidores futuros sem alterar o produtor

---

## Consequências

### Positivas

- Configuração local trivial; equipe sobe o broker com um único serviço no `docker-compose`
- DLQ e retry configuráveis por fila sem código adicional
- Interface de administração visual facilita debug e inspeção de mensagens

### Negativas / Trade-offs

- **Sem retenção de longo prazo** — RabbitMQ não mantém histórico de mensagens após o ACK; replay de eventos históricos depende do endpoint [RF-02](../negocio/requisitos.md#rf-02) do Serviço de Lançamentos
- **Modelo push** — consumidores recebem mensagens automaticamente; picos acima da capacidade de processamento exigem prefetch configurado adequadamente
- **Migração futura** — se o volume crescer para ordem de grandeza de milhões de eventos/hora, uma migração para Kafka seria justificada; o contrato de mensagens (schema do evento) deve ser mantido estável para facilitar essa transição
