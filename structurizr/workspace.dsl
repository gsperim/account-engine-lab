workspace "Fluxo de Caixa Diário" "Controle de Fluxo de Caixa" {

    model {

        caixa = person "Caixa" "Operador que registra débitos e créditos no momento da transação."
        gestor = person "Gestor" "Responsável por acompanhar o saldo consolidado e tomar decisões financeiras."

        pdv = softwareSystem "Sistema PDV" "Ponto de venda externo que envia lançamentos via API — canal máquina-a-máquina." {
            tags "External"
        }

        # ── Plataforma de Observabilidade ─────────────────────────────────────
        observabilidade = softwareSystem "Plataforma de Observabilidade" "Stack PLT + OpenTelemetry. Coleta, processa e armazena traces, métricas, logs e profiles. ADR-015." {
            tags "Observability"

            otelCollector = container "OTEL Collector" {
                description "Pipeline central. Recebe OTLP gRPC/HTTP, redacta PII (CPF, CNPJ, e-mail, cartão) e exporta por sinal para os backends. ADR-015, ADR-016."
                technology "OpenTelemetry Collector Contrib 0.113 · :4317/:4318"
                tags "ObsInfra"
            }

            promtail = container "Promtail" {
                description "Agente de coleta de logs Docker. Captura stdout de todos os containers. Pipeline de PII redaction antes de enviar ao Loki. ADR-016."
                technology "Grafana Promtail 3.3 · Docker socket"
                tags "ObsInfra"
            }

            prometheus = container "Prometheus" {
                description "Armazenamento de métricas (15 dias). Avalia regras de alerta (burn rate, SLOs) e dispara para o Alertmanager. 14 scrape targets."
                technology "Prometheus 3.1 · PromQL · :9090"
                tags "ObsBackend"
            }

            alertmanager = container "Alertmanager" {
                description "Agrega e roteia alertas por severidade. Receivers: Telegram (dev), Slack + PagerDuty (prod). Inibições e silences."
                technology "Alertmanager 0.27 · :9093"
                tags "ObsBackend"
            }

            loki = container "Loki" {
                description "Armazenamento de logs (31 dias). Indexação por labels: service, level, trace_id."
                technology "Grafana Loki 3.3 · LogQL · :3100"
                tags "ObsBackend"
            }

            tempo = container "Tempo" {
                description "Armazenamento de traces distribuídos (15 dias). Compatível com Jaeger. Correlação com logs e profiles via trace_id."
                technology "Grafana Tempo 2.6 · OTLP · :3200"
                tags "ObsBackend"
            }

            pyroscope = container "Pyroscope" {
                description "Continuous profiling — CPU, heap, goroutines. Drill-down de span → flamegraph → linha de código."
                technology "Grafana Pyroscope 1.7 · :4040"
                tags "ObsBackend"
            }

            blackbox = container "Blackbox Exporter" {
                description "Sondas sintéticas HTTP. Monitora uptime e latência de 7 endpoints (Traefik, Keycloak, Grafana, Prometheus, Loki, Tempo, RabbitMQ)."
                technology "Prometheus Blackbox 0.25 · :9115"
                tags "ObsInfra"
            }

            grafana = container "Grafana" {
                description "UI unificada. Correlaciona traces → logs → métricas → profiles. Datasources: Prometheus, Loki, Tempo, Pyroscope, Alertmanager."
                technology "Grafana 11.4 · :3000"
                tags "ObsUI"
            }
        }

        # ── Sistema de Negócio ────────────────────────────────────────────────
        sistema = softwareSystem "Sistema de Fluxo de Caixa" "Controla lançamentos financeiros e consolida saldos diários." {
            !docs overview.md

            keycloak = container "Keycloak" {
                description "Servidor OAuth2/OIDC. Emite JWTs RS256, expõe JWKS para validação local. Authorization Code + PKCE (usuários) e Client Credentials (PDV). ADR-014."
                technology "Keycloak 26 · OAuth2 · OIDC · :8180"
                tags "Identity"
            }

            frontend = container "Aplicação Web" {
                description "Interface para registro de lançamentos (Caixa) e consulta de saldo consolidado (Gestor)."
                technology "Web · HTTPS"
                tags "Web"
            }

            gateway = container "API Gateway" {
                description "Ponto de entrada único. Valida JWT via JWKS cache local (ADR-004), aplica rate limiting (NFR-07) e roteia para os serviços. Traefik local / AWS API Gateway prod."
                technology "Traefik 3 (local) · AWS API Gateway HTTP API (prod)"
                tags "Gateway"
            }

            lancamentos = container "Serviço de Lançamentos" {
                description "Registra débitos e créditos com garantia de entrega via Outbox Pattern (ADR-003). Não pode cair se a Consolidação estiver indisponível (NFR-01)."
                technology "REST API · PostgreSQL · OTEL SDK"
                tags "Service"
            }

            outboxRelay = container "Outbox Relay" {
                description "Polling da tabela outbox → publicação no broker com at-least-once delivery. Propaga trace_id em headers AMQP. ADR-012."
                technology "Worker · polling 500ms · OTEL SDK"
                tags "Service"
            }

            consolidado = container "Serviço de Consolidação Diária" {
                description "Consome eventos e mantém saldo pré-computado por data com cache-aside Redis. Suporta 50 req/s com até 5% de perda (NFR-02)."
                technology "Event consumer · REST API · PostgreSQL · Redis · OTEL SDK"
                tags "Service"
            }

            broker = container "Message Broker" {
                description "Desacopla Lançamentos de Consolidação via fila durável com DLQ. Garante at-least-once delivery."
                technology "RabbitMQ 3.13 · AMQP · topic exchange"
                tags "Broker"
            }

            dbLancamentos = container "PostgreSQL (Lançamentos)" {
                description "Banco do Serviço de Lançamentos. Append-only, NUMERIC(15,2), UUID. Inclui tabela outbox. ADR-012."
                technology "PostgreSQL 16"
                tags "Database"
            }

            dbConsolidado = container "PostgreSQL (Consolidação)" {
                description "Banco do Serviço de Consolidação. Read model + idempotência + saldos pré-computados. ADR-012."
                technology "PostgreSQL 16"
                tags "Database"
            }

            cache = container "Redis" {
                description "Cache de saldos por data (saldo:{YYYY-MM-DD}). TTL 60s, invalidação ativa pós-evento. ADR-012."
                technology "Redis 7 · AOF · cache-aside"
                tags "Cache"
            }
        }

        # ── Relacionamentos — System Context ──────────────────────────────────
        caixa -> sistema "Registra lançamentos e consulta histórico" "HTTPS"
        gestor -> sistema "Consulta saldo consolidado" "HTTPS"
        pdv -> sistema "Envia lançamentos via API" "REST/HTTPS · OAuth2"
        sistema -> observabilidade "Envia telemetria" "OTLP gRPC"

        # ── Relacionamentos — Containers de Negócio ───────────────────────────
        caixa -> frontend "Registra lançamentos" "HTTPS"
        gestor -> frontend "Consulta saldo consolidado" "HTTPS"

        frontend -> keycloak "Redireciona para login / troca código por token" "OAuth2 Authorization Code + PKCE"
        pdv -> keycloak "Obtém access token" "OAuth2 Client Credentials"

        frontend -> gateway "Envia requisições autenticadas" "REST/HTTPS · Bearer JWT"
        pdv -> gateway "Envia lançamentos via API" "REST/HTTPS · Bearer JWT"

        gateway -> keycloak "Obtém chaves públicas (JWKS) — cache local" "HTTPS"
        gateway -> lancamentos "Roteia requisições de lançamento" "REST/HTTP · claims em headers"
        gateway -> consolidado "Roteia requisições de saldo" "REST/HTTP · claims em headers"

        lancamentos -> dbLancamentos "Persiste lançamentos e outbox (mesma tx)" "SQL/TLS"
        lancamentos -> otelCollector "Envia traces, métricas e logs" "OTLP gRPC"

        outboxRelay -> dbLancamentos "Faz polling da tabela outbox" "SQL/TLS"
        outboxRelay -> broker "Publica eventos com trace_id" "AMQP · publisher confirm"
        outboxRelay -> otelCollector "Envia traces e logs" "OTLP gRPC"

        broker -> consolidado "Entrega eventos (at-least-once)" "AMQP"
        consolidado -> dbConsolidado "Upsert idempotente de saldo" "SQL/TLS"
        consolidado -> cache "Lê saldo e invalida após evento" "Redis Protocol"
        consolidado -> otelCollector "Envia traces, métricas e logs" "OTLP gRPC"

        # ── Relacionamentos — Observabilidade Interna ─────────────────────────
        otelCollector -> tempo "Exporta traces" "OTLP"
        otelCollector -> prometheus "Exporta métricas" "remote write"
        otelCollector -> loki "Exporta logs (pós-redação PII)" "push"

        promtail -> loki "Envia logs Docker (pós-redação PII)" "push"

        prometheus -> alertmanager "Dispara alertas" "HTTP"

        blackbox -> gateway "Probe HTTP /ping" "HTTP"
        blackbox -> keycloak "Probe HTTP /health/ready" "HTTP"
        blackbox -> grafana "Probe HTTP /api/health" "HTTP"
        blackbox -> prometheus "Probe HTTP /-/healthy" "HTTP"
        blackbox -> loki "Probe HTTP /ready" "HTTP"
        blackbox -> tempo "Probe HTTP /ready" "HTTP"

        grafana -> prometheus "Query métricas" "PromQL"
        grafana -> loki "Query logs" "LogQL"
        grafana -> tempo "Query traces" "TraceQL"
        grafana -> pyroscope "Query profiles" "HTTP"
        grafana -> alertmanager "Lê alertas e silences" "HTTP"
    }

    views {

        systemContext sistema "contexto" {
            title "Contexto do Sistema — Fluxo de Caixa Diário"
            include *
            autoLayout
        }

        container sistema "containers" {
            title "Containers — Sistema de Negócio"
            include *
            include observabilidade
            exclude otelCollector
            exclude promtail
            exclude prometheus
            exclude alertmanager
            exclude loki
            exclude tempo
            exclude pyroscope
            exclude blackbox
            exclude grafana
            autoLayout
        }

        container observabilidade "observabilidade-containers" {
            title "Containers — Plataforma de Observabilidade"
            include *
            include lancamentos
            include outboxRelay
            include consolidado
            autoLayout
        }

        styles {
            element "Person" {
                background "#08427b"
                color "#ffffff"
                shape "Person"
            }
            element "External" {
                background "#999999"
                color "#ffffff"
                opacity 50
            }
            element "Identity" {
                background "#6f42c1"
                color "#ffffff"
                shape "RoundedBox"
            }
            element "Gateway" {
                background "#e67e00"
                color "#ffffff"
                shape "RoundedBox"
            }
            element "Service" {
                background "#438dd5"
                color "#ffffff"
            }
            element "Broker" {
                background "#438dd5"
                color "#ffffff"
                shape "Pipe"
            }
            element "Database" {
                background "#1e6b3a"
                color "#ffffff"
                shape "Cylinder"
            }
            element "Cache" {
                background "#a93226"
                color "#ffffff"
                shape "Cylinder"
            }
            element "Web" {
                background "#438dd5"
                color "#ffffff"
                shape "WebBrowser"
            }
            element "Observability" {
                background "#2d6a4f"
                color "#ffffff"
                shape "RoundedBox"
            }
            element "ObsUI" {
                background "#2d6a4f"
                color "#ffffff"
                shape "WebBrowser"
            }
            element "ObsBackend" {
                background "#2d6a4f"
                color "#ffffff"
                shape "Cylinder"
            }
            element "ObsInfra" {
                background "#1a4035"
                color "#ffffff"
                shape "RoundedBox"
            }
        }

        themes theme.json

    }

}
