---
tags:
  - evolucoes
  - backlog
  - arquitetura
---

# Evoluções Futuras e Backlog Técnico

**Papel:** 🧩 Arquiteto de Soluções · 🛠️ Engenheiro de Software  
**Data da análise:** 2026-05-18

Este documento registra o que foi deixado fora do escopo com justificativa técnica. Os gaps técnicos identificados na análise do código foram todos resolvidos e incorporados à implementação.

---

## Decisões de escopo — deixados para versões futuras

Estes itens foram identificados durante o desenvolvimento e conscientemente adiados. A decisão de escopo em cada caso está justificada abaixo.

---

### 1. Backoffice de reprocessamento da DLQ

**Escopo:** reproduzir manualmente mensagens da Dead-Letter Queue para a fila principal, com trilha de auditoria.

**Estado atual:**  
A DLQ `consolidacao.lancamentos.dlq` está configurada no RabbitMQ via `RabbitConfig` — mensagens que excedem o limite de retentativas são roteadas automaticamente. O volume da fila é monitorado pelas métricas nativas do RabbitMQ (Prometheus scraping via management plugin), visíveis no Grafana. Não há consumer na DLQ.

**Por que não há consumer:**  
Um consumer na DLQ consumiria (ackaria) as mensagens, impedindo a análise posterior da causa raiz. Mensagens na DLQ devem permanecer lá até investigação manual — o volume crescente é o sinal de alerta. O reprocessamento exige decisão humana e audit trail.

**Caminho de evolução:**  
Endpoint `POST /admin/dlq/{messageId}/replay` com `ROLE_ADMIN`, persistência em `audit_log`, e painel no Grafana com histórico de volume e TTL da fila.

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



