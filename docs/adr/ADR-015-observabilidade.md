---
tags:
  - adr
  - decisao
  - observabilidade
---

# ADR-015 — Stack de Observabilidade: PLT + OpenTelemetry

**Papéis:** 👁️ Arquiteto de Observabilidade · 🔐 DevSecOps  
**Data:** 2026-05-09  
**Status:** Aceito  
**Requisitos:** [NFR-04](../negocio/requisitos.md#nfr-04), [NFR-02](../negocio/requisitos.md#nfr-02), [NFR-06](../negocio/requisitos.md#nfr-06)

---

## Contexto

[NFR-04](../negocio/requisitos.md#nfr-04) exige logs estruturados, métricas e traces em 100% das requisições. A escolha do stack de observabilidade afeta dois ambientes com requisitos diferentes:

- **Local (docker-compose):** deve rodar sem dependência de serviço externo — [C-01](../negocio/requisitos.md#c-01)
- **Produção (AWS):** custo operacional e integração com o ecossistema existente

O ponto crítico: qualquer stack que só funciona em produção (ex: CloudWatch puro) impossibilita observar o sistema localmente durante desenvolvimento e testes — cria um ponto cego justamente onde bugs são introduzidos.

---

## Alternativas Consideradas

### AWS Managed (CloudWatch Logs + CloudWatch Metrics + X-Ray)

Serviços gerenciados da AWS, já parcialmente provisionados no Terraform ([ADR-011](ADR-011-excelencia-operacional.md)).

**Por que foi descartado como stack principal:**

- **Sem execução local** — CloudWatch e X-Ray não têm emuladores locais funcionais. Desenvolver e debugar sem observabilidade é produtividade drasticamente reduzida.
- **Vendor lock-in na instrumentação** — o SDK do X-Ray é proprietário; migrar para outro provider exigiria reinstrumentar todos os serviços.
- **Custo variável alto em logs** — CloudWatch Logs cobra $0,50/GB ingerido. Em desenvolvimento ativo com logs verbosos, o custo pode surpreender.

CloudWatch permanece como **destino complementar em produção** — o OTEL Collector pode exportar para CloudWatch simultaneamente ao PLT, sem alterar o código dos serviços.

### ELK Stack (Elasticsearch + Logstash + Kibana)

Stack maduro e amplamente usado para logs.

**Por que foi descartado:**

- Elasticsearch consome ≥ 2 GB de RAM só para inicializar — inviável no docker-compose de desenvolvimento junto com todos os outros serviços.
- Três pilares (logs, métricas, traces) exigiriam ELK + Prometheus + Jaeger separados — quatro UIs, quatro pipelines. O PLT unifica os três no Grafana.
- Licença Elastic mudou em 2021; ambiguidade jurídica para uso comercial.

---

## Decisão

Adotar **PLT + OpenTelemetry Collector** como stack unificado de observabilidade.

```
Serviços
  └── OTEL SDK (traces + métricas + logs)
        └── OTEL Collector
              ├── Traces  → Tempo
              ├── Métricas → Prometheus
              └── Logs    → Loki
                            └── Grafana (UI unificada)
```

| Componente | Função | Versão |
|-----------|--------|--------|
| **OpenTelemetry SDK** | Instrumentação nos serviços — agnóstica de backend | Latest estável |
| **OTEL Collector** | Pipeline central: recebe OTLP, processa, distribui | `0.113.0` |
| **Tempo** | Backend de traces distribuídos | `2.6.0` |
| **Prometheus** | Backend de métricas com remote write | `v3.1.0` |
| **Loki** | Backend de logs estruturados | `3.3.0` |
| **Grafana** | UI unificada — correlação traces↔logs↔métricas | `11.4.0` |

### Por que OpenTelemetry como camada de instrumentação

O OTEL SDK separa **o que instrumentar** (código) de **para onde enviar** (configuração). Em produção, o OTEL Collector pode ter dois exporters simultâneos sem mudar uma linha dos serviços:

```yaml
# otel-collector.yml — produção
exporters:
  otlp/tempo: ...        # PLT no EKS
  awsxray: ...           # X-Ray (backup/compliance)
  awscloudwatchmetrics:  # CloudWatch Metrics (dashboards executivos)
```

### Correlação entre pilares

O Grafana unifica os três pilares com navegação bidirecional:
- **Trace → Logs**: ao abrir um trace no Tempo, filtra automaticamente os logs do Loki pelo `trace_id`
- **Trace → Métricas**: mostra as métricas do serviço no período do trace
- **Alert → Trace**: alertas do Grafana linkam para exemplares (traces de requisições problemáticas)

Isso é possível porque o OTEL SDK injeta o mesmo `trace_id` nos logs, spans e métricas de cada requisição.

---

## Consequências

### Positivas

- **Paridade local/produção** — o mesmo pipeline de observabilidade roda em docker-compose e em EKS
- **Cloud-agnostic** — trocar o backend (Tempo → Jaeger, Loki → CloudWatch Logs, Prometheus → Thanos) é mudança de configuração do Collector, não de código
- **UI unificada** — Grafana correlaciona traces, logs e métricas sem alternar ferramentas
- **Instrumentação única** — OTEL SDK é agnóstico; os serviços não conhecem o backend

### Negativas / Trade-offs

- **Seis containers adicionais** no docker-compose — aumenta o consumo de memória local (~1.5 GB adicionais para o stack completo)
- **Operação do PLT em produção** — em EKS, Prometheus, Loki e Tempo precisam de storage persistente e alta disponibilidade. Mitigado pela opção de usar Grafana Cloud como SaaS no lugar do stack auto-hospedado.
- **Curva de configuração** — o OTEL Collector tem configuração mais verbosa que SDKs proprietários
