---
tags:
  - seguranca
  - conformidade
  - iso37301
  - normas
---

# Conformidade e Evidências Normativas

**Perspectiva:** 🏛️ Arquiteto Corporativo · 🔒 Arquiteto de Segurança  
**Framework:** ISO 37301 (Compliance Management Systems)  
**Requisito:** [NFR-09](../negocio/requisitos.md#nfr-09) — rastreabilidade e trilha de auditoria

---

## Normas Adotadas e Escopo de Aplicação

Este sistema adota o conjunto de referências normativas descrito abaixo. Nenhuma delas é de cumprimento regulatório obrigatório para o escopo atual (sistema interno de controle de caixa, sem processamento de cartões ou escrituração COSIF/Bacen). São adotadas como **boas práticas de mercado** para garantir qualidade arquitetural, rastreabilidade e preparação para cenários regulatórios futuros.

| Norma | Escopo de aplicação | Forma de adoção |
|-------|-------------------|----------------|
| **ISO 4217** | Padronização de códigos de moeda | Documentação — escopo atual é single-currency (BRL implícito) |
| **ISO 8601** | Datas, horários e timestamps | Implementação — `LocalDate` / `OffsetDateTime` + `format: date` no OpenAPI |
| **ISO 20022** | Semântica de conceitos financeiros | Referência — terminologia de débito/crédito alinhada à norma |
| **ISO/IEC 27001** | Controles de segurança da informação | Implementação parcial — ver [mapeamento de controles](index.md#mapeamento-iso-27001) |
| **ISO 22301** | Continuidade operacional e recuperação | Implementação — ver [Continuidade Operacional](continuidade.md) |
| **ISO 31000** | Gestão de riscos operacionais | Implementação — risk register em [Continuidade Operacional](continuidade.md#registro-de-riscos) |
| **ISO 37301** | Governança de conformidade e evidências | Este documento |
| **OWASP ASVS** | Segurança verificável de aplicação e APIs | Implementação — ver [Matriz ASVS](asvs.md) |

---

## Evidências de Conformidade por Requisito

### ISO 8601 — Padronização de Datas e Timestamps

| Ponto de conformidade | Implementação | Evidência |
|----------------------|---------------|-----------|
| `data_competencia` em formato `YYYY-MM-DD` | `LocalDate` + `format: date` no OpenAPI | Contrato `lancamentos.yaml` linha 98 |
| Timestamps de criação em UTC | `@CreationTimestamp` → `OffsetDateTime` | `LancamentoJpaEntity.criadoEm` |
| Timestamps no payload de resposta ISO 8601 | Jackson `DateTimeFormat` padrão | `criado_em` no response body |
| Logs com timestamp ISO 8601 | Formato Logstash JSON (`@timestamp`) | `application.properties: logging.structured.format.console=logstash` |

### ISO 20022 — Semântica Financeira

A ISO 20022 define um catálogo de conceitos financeiros padronizados — *credit transfer*, *debit transfer*, *payment*, *statement*. O sistema não implementa os schemas XML/JSON da norma (fora do escopo), mas alinha sua terminologia de domínio:

| Conceito ISO 20022 | Equivalente no sistema | Artefato |
|--------------------|----------------------|---------|
| *CreditTransfer* | `CREDITO` | `TipoMovimento.CREDITO` |
| *DebitTransfer* | `DEBITO` | `TipoMovimento.DEBITO` |
| *Statement* (extrato) | Saldo consolidado diário | `SaldoConsolidado` aggregate |
| *ReversalTransaction* | Estorno | `POST /registros/{id}/estorno` |
| *UniqueTransactionReference* | `Idempotency-Key` (UUID) | Header HTTP obrigatório |
| *ValueDate* | `data_competencia` | Campo ISO 8601 no lançamento |

### ISO 37301 — Trilha de Auditoria e Governança

A ISO 37301 exige que a organização demonstre:

1. **Identificação das obrigações de conformidade** — este documento
2. **Controles para atender essas obrigações** — mapeados abaixo
3. **Evidências de que os controles funcionam** — artefatos implementados
4. **Mecanismo de detecção e correção de não conformidades** — reconciliação + DLQ

#### Trilha de Auditoria Implementada

Cada operação de escrita no sistema deixa evidências imutáveis que permitem responder às perguntas: *quem fez, o quê, quando e com qual valor*.

| Pergunta | Dado capturado | Onde |
|----------|---------------|------|
| **Quem** | `operador_id` = claim `sub` do JWT (UUID Keycloak) | Coluna `lancamentos.operador_id` |
| **O quê** | `tipo` (CREDITO/DEBITO), `valor`, `descricao` | Colunas da tabela `lancamentos` |
| **Quando** | `criado_em` timestamp UTC imutável | `@CreationTimestamp` — não atualizável |
| **Qual chave de transação** | `id` = UUID v4 gerado pelo banco | PK `lancamentos.id` |
| **Idempotência** | `Idempotency-Key` + `payload_hash` SHA-256 | Coluna `lancamentos.idempotency_key` + `payload_hash` |
| **Estorno de qual lançamento** | `estorno_de` UUID do original | Coluna `lancamentos.estorno_de` |
| **Rastreabilidade distribuída** | `correlation_id` + `traceId` em logs | MDC → Loki + Tempo |

#### Imutabilidade como Controle de Conformidade

```sql
-- Lançamentos são append-only: sem UPDATE, sem DELETE sobre registros confirmados.
-- Correções são feitas via estorno (novo lançamento com tipo inverso).
-- O único campo atualizável é estornado_por — preenchido pelo handler de estorno.
-- Garantia de banco + ausência de UPDATE/DELETE nos repos da camada de persistência.
```

A imutabilidade é garantida em duas camadas:
- **Schema**: ausência de comandos `UPDATE`/`DELETE` nos adapters de persistência (`LancamentoRepositoryAdapter`)
- **Negócio**: [RF-08](../negocio/requisitos.md#rf-08) define estorno como um *novo* lançamento, nunca modificação do original

#### Rastreabilidade de Eventos com Falha

Eventos que não puderam ser processados pelo Serviço de Consolidação chegam à **Dead Letter Queue (DLQ)**. O `DlqConsumer` registra métricas (`consolidado_dlq_mensagens_total`) e logs estruturados para cada mensagem na DLQ — sem reprocessamento automático. Isso garante que falhas de processamento sejam **visíveis, mensuráveis e auditáveis**, sem risco de reprocessamento duplicado não controlado.

#### Reconciliação como Mecanismo de Detecção

O `ReconciliacaoDiariaJob` executa diariamente às 02:00 e compara:
- Totais calculados pelo Serviço de Consolidação
- Totais calculados diretamente nos lançamentos via `GET /lancamentos/registros/resumo`

Divergências são registradas na métrica `saldo_reconciliado_divergencias_total` e alertadas via Grafana. Esse mecanismo responde ao requisito ISO 37301 de **detecção proativa de não conformidades**.

---

## Matriz de Rastreabilidade Normativa

| Norma | Requisito do sistema | Controle implementado | Artefato de evidência |
|-------|---------------------|----------------------|----------------------|
| ISO 8601 | Timestamps consistentes e portáveis | `OffsetDateTime` + `format: date-time` OpenAPI | Contratos em `contracts/openapi/` |
| ISO 20022 | Terminologia financeira padronizada | `TipoMovimento.{CREDITO,DEBITO}` + `estorno_de` | Domain model Java |
| ISO/IEC 27001 A.9 | Controle de acesso por identidade | JWT + escopos + RBAC | [seguranca/index.md](index.md) |
| ISO/IEC 27001 A.10 | Criptografia de dados | KMS CMK + TLS obrigatório em produção | [ADR-010](../adr/ADR-010-seguranca.md) |
| ISO/IEC 27001 A.12 | Logging e monitoramento de operações | Loki + Grafana + Prometheus + Tempo | [observabilidade](../observabilidade/index.md) |
| ISO/IEC 27001 A.16 | Gestão de incidentes de segurança | DLQ + `DlqConsumer` + Security Hub (prod) | `DlqConsumer.java` · [ADR-010](../adr/ADR-010-seguranca.md) |
| ISO/IEC 27001 A.17 | Continuidade de negócios | Outbox + Circuit Breaker + Chaos Engineering | [continuidade.md](continuidade.md) |
| ISO 22301 | RTO/RPO para operações críticas | `/admin/reconstruir` + Outbox Relay | [continuidade.md](continuidade.md) |
| ISO 31000 | Risk register com controles mapeados | Chaos Engineering + reconciliação | [continuidade.md](continuidade.md#registro-de-riscos) |
| ISO 37301 | Trilha de auditoria imutável | `operadorId` + timestamps + append-only | `LancamentoJpaEntity.java` |
| OWASP ASVS L1 | Controles de segurança verificáveis | 26 controles L1 atendidos | [asvs.md](asvs.md) |
| OWASP ASVS L2 | Defense in depth | 8 controles L2 adicionais atendidos | [asvs.md](asvs.md) |

---

## Lacunas e Roadmap de Conformidade

| Norma | Gap atual | Impacto | Roadmap |
|-------|-----------|---------|---------|
| ISO 4217 | Campo `moeda` ausente nas entidades — BRL implícito | Baixo — escopo single-currency | Adicionar `moeda VARCHAR(3) DEFAULT 'BRL'` na evolução multi-moeda |
| ISO/IEC 27001 A.18 | Revisão formal de conformidade periódica não automatizada | Médio | Integrar checklist de conformidade ao pipeline de release |
| ISO 37301 | Backoffice de DLQ sem audit trail completo de replay | Médio | Implementar replay controlado com log de quem iniciou, quando e com qual justificativa |
| OWASP ASVS L2 V14.3 | SAST não implementado no `ci.yml` | Médio | SpotBugs + SonarCloud no pipeline |
