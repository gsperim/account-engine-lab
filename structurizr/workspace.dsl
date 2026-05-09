workspace "Fluxo de Caixa Diário" "Controle de Fluxo de Caixa" {

    model {

        caixa = person "Caixa" "Operador que registra débitos e créditos no momento da transação."
        gestor = person "Gestor" "Responsável por acompanhar o saldo consolidado e tomar decisões financeiras."

        pdv = softwareSystem "Sistema PDV" "Ponto de venda externo que envia lançamentos diretamente via API — canal alternativo ao frontend para operações integradas." {
            tags "External"
        }

        sistema = softwareSystem "Sistema de Fluxo de Caixa" "Controla lançamentos financeiros e consolida saldos diários." {
            !docs overview.md

            keycloak = container "Keycloak" {
                description "Servidor de identidade OAuth2/OIDC. Emite JWTs assinados com RS256, expõe endpoint JWKS para validação local sem roundtrip. Suporta Authorization Code + PKCE (Caixa/Gestor) e Client Credentials (PDV). Decisão: ADR-014."
                technology "Keycloak 24 · OAuth2 · OIDC"
                tags "Identity"
            }

            frontend = container "Aplicação Web" {
                description "Interface para registro de lançamentos pelo Caixa e consulta de saldo consolidado pelo Gestor."
                technology "Web · HTTPS"
                tags "Web"
            }

            gateway = container "API Gateway" {
                description "Ponto de entrada único para todas as requisições. Valida JWT via JWKS cache local (ADR-004), aplica rate limiting (NFR-07) e roteia para os serviços internos. Local: Traefik. Produção: AWS API Gateway HTTP API."
                technology "Traefik 3 (local) · AWS API Gateway HTTP API (prod)"
                tags "Gateway"
            }

            lancamentos = container "Serviço de Lançamentos" {
                description "Registra débitos e créditos. Persiste na tabela outbox na mesma transação para garantia de entrega (ADR-003). Não pode ficar indisponível se a Consolidação cair (NFR-01)."
                technology "REST API · PostgreSQL · Outbox Pattern"
                tags "Service"
            }

            outboxRelay = container "Outbox Relay" {
                description "Processo que faz polling da tabela outbox e publica eventos no broker. Garante entrega at-least-once sem dependência de trigger ou CDC (ADR-012)."
                technology "Worker · polling 500ms"
                tags "Service"
            }

            consolidado = container "Serviço de Consolidação Diária" {
                description "Consome eventos de lançamento e mantém saldo pré-computado por dia. Serve consultas com cache-aside no Redis. Suporta 50 req/s com até 5% de perda (NFR-02)."
                technology "Event consumer · REST API · PostgreSQL · Redis"
                tags "Service"
            }

            broker = container "Message Broker" {
                description "Desacopla Lançamentos de Consolidação via fila durável com DLQ. Garante at-least-once delivery e absorve eventos quando a Consolidação está indisponível."
                technology "RabbitMQ 3.13 · AMQP · topic exchange"
                tags "Broker"
            }

            dbLancamentos = container "PostgreSQL (Lançamentos)" {
                description "Banco exclusivo do Serviço de Lançamentos. Armazena lançamentos (append-only) e tabela outbox. Isolado por P-04 (database per service)."
                technology "PostgreSQL 16"
                tags "Database"
            }

            dbConsolidado = container "PostgreSQL (Consolidação)" {
                description "Banco exclusivo do Serviço de Consolidação. Armazena lançamentos processados (read model) e saldos pré-computados por dia. Isolado por P-04."
                technology "PostgreSQL 16"
                tags "Database"
            }

            cache = container "Redis" {
                description "Cache de leitura para saldos consolidados por data (saldo:{YYYY-MM-DD}). Padrão cache-aside com TTL 60s e invalidação ativa após cada evento processado."
                technology "Redis 7 · AOF · cache-aside"
                tags "Cache"
            }

        }

        # ── Relacionamentos — System Context ───────────────────────────────────
        caixa -> sistema "Registra lançamentos e consulta histórico" "HTTPS"
        gestor -> sistema "Consulta saldo consolidado" "HTTPS"
        pdv -> sistema "Envia lançamentos via API" "REST/HTTPS · OAuth2"

        # ── Relacionamentos — Container Level ──────────────────────────────────
        caixa -> frontend "Registra lançamentos" "HTTPS"
        gestor -> frontend "Consulta saldo consolidado" "HTTPS"

        frontend -> keycloak "Redireciona para login / troca código por token" "OAuth2 Authorization Code + PKCE"
        pdv -> keycloak "Obtém access token" "OAuth2 Client Credentials"

        frontend -> gateway "Envia requisições autenticadas" "REST/HTTPS · Bearer JWT"
        pdv -> gateway "Envia lançamentos via API" "REST/HTTPS · Bearer JWT"

        gateway -> keycloak "Obtém chaves públicas (JWKS) — uma vez, depois cache local" "HTTPS"
        gateway -> lancamentos "Roteia requisições de lançamento" "REST/HTTP · claims em headers"
        gateway -> consolidado "Roteia requisições de saldo" "REST/HTTP · claims em headers"

        lancamentos -> dbLancamentos "Persiste lançamentos e outbox (mesma tx)" "SQL/TLS"
        outboxRelay -> dbLancamentos "Faz polling da tabela outbox" "SQL/TLS"
        outboxRelay -> broker "Publica eventos de domínio" "AMQP · publisher confirm"

        broker -> consolidado "Entrega eventos (at-least-once)" "AMQP"
        consolidado -> dbConsolidado "Upsert idempotente de saldo" "SQL/TLS"
        consolidado -> cache "Lê saldo (cache-aside) e invalida após evento" "Redis Protocol/TLS"

    }

    views {

        systemContext sistema "contexto" {
            title "Contexto do Sistema — Fluxo de Caixa Diário"
            include *
            autoLayout
        }

        container sistema "containers" {
            title "Containers — Fluxo de Caixa Diário"
            include *
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
        }

        themes theme.json

    }

}
