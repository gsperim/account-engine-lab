workspace "Fluxo de Caixa Diário" "Controle de Fluxo de Caixa" {

    model {

        caixa = person "Caixa" "Operador que registra débitos e créditos no momento da transação."
        gestor = person "Gestor" "Responsável por acompanhar o saldo consolidado e tomar decisões financeiras."

        pdv = softwareSystem "Sistema PDV" "Ponto de venda externo que envia lançamentos via API — canal máquina-a-máquina." {
            tags "External"
        }

        idp = softwareSystem "Identity Provider" "Capacidade corporativa de identidade e acesso. Emite JWTs RS256 e expõe JWKS para validação local. Dev: Keycloak 26 / Prod: AWS Cognito ou equivalente corporativo. ADR-014." {
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

            # ── Serviço de Lançamentos ─────────────────────────────────────
            lancamentos = container "Serviço de Lançamentos" {
                description "Registra débitos e créditos com garantia de entrega via Outbox Pattern (ADR-003). Não pode cair se a Consolidação estiver indisponível (NFR-01)."
                technology "Spring Boot 3.5 · Java 21 · REST API · PostgreSQL · OTEL SDK"
                tags "Service"

                # Adapters IN
                lCtrl = component "LancamentoController" {
                    description "Adapter REST IN. Implementa LancamentosApi gerada pelo OpenAPI Generator (ADR-017). Extrai operadorId do JWT via jwt.getSubject() com fallback para preferred_username."
                    technology "Spring MVC · @RestController · SecurityFilterChain"
                    tags "AdapterIn"
                }

                # Application — Use Cases
                lRegSvc = component "RegistrarLancamentoService" {
                    description "Orquestra o registro de lançamentos: verifica idempotência (existsPorId), valida payload_hash SHA-256, persiste lançamento + outbox na mesma transação e publica AuditEvento."
                    technology "Java · @Service · @Transactional"
                    tags "Application"
                }
                lBusSvc = component "BuscarLancamentoService" {
                    description "Busca lançamento por ID. Retorna Optional — o controller traduz para 200 ou 404."
                    technology "Java · @Service"
                    tags "Application"
                }
                lLstSvc = component "ListarLancamentosService" {
                    description "Lista lançamentos por data de competência com paginação. Retorna resultado paginado com total_elements e total_pages."
                    technology "Java · @Service"
                    tags "Application"
                }
                lEstSvc = component "EstornarLancamentoService" {
                    description "Registra estorno rastreável. UUID do estorno é derivado deterministicamente do original (UUID.nameUUIDFromBytes) — garante idempotência em retentativas."
                    technology "Java · @Service · @Transactional"
                    tags "Application"
                }
                lResSvc = component "ResumoDiarioService" {
                    description "Calcula totais de crédito, débito e contagem de lançamentos por data de competência. Consumido pela reconciliação diária do Consolidado."
                    technology "Java · @Service"
                    tags "Application"
                }

                # Adapters OUT — Persistência
                lRepo = component "LancamentoRepositoryAdapter" {
                    description "Adapter JPA OUT. Implementa LancamentoRepository (port). Operações: existePorId, salvar, buscarPorId, listarPorData. Tabela append-only — sem UPDATE nem DELETE sobre registros confirmados."
                    technology "Spring Data JPA · H2 (test) · PostgreSQL 16"
                    tags "AdapterOut"
                }
                lOutboxRepo = component "OutboxRepositoryAdapter" {
                    description "Adapter JPA OUT. Implementa OutboxPort. Persiste entrada na tabela outbox junto com o lançamento na mesma transação. Índice parcial WHERE publicado = false otimiza o polling."
                    technology "Spring Data JPA · PostgreSQL 16"
                    tags "AdapterOut"
                }

                # Adapters OUT — Audit
                lAuditPub = component "AuditPublisherAdapter" {
                    description "Adapter OUT. Implementa AuditPublisher (port hexagonal). Publica Spring ApplicationEvent com AuditEvento — não grava no banco diretamente."
                    technology "Spring ApplicationEventPublisher"
                    tags "AdapterOut"
                }
                lAuditListener = component "AuditEventListener" {
                    description "Listener Spring. @TransactionalEventListener(AFTER_COMMIT) + @Async: persiste audit_log após o commit da transação de negócio. Falha silenciosa — nunca propaga para o caller."
                    technology "Spring Events · Virtual Threads (@Async)"
                    tags "AdapterOut"
                }
            }

            # ── Outbox Relay ───────────────────────────────────────────────
            outboxRelay = container "Outbox Relay" {
                description "Polling da tabela outbox → publicação no broker com at-least-once delivery. Propaga trace_id em headers AMQP. @Scheduled(fixedDelay=5000ms). ADR-003, ADR-012."
                technology "Spring Boot · @Scheduled · OTEL SDK"
                tags "Service"

                lRelayJob = component "OutboxRelay" {
                    description "@Scheduled(fixedDelay=5000ms): busca registros pendentes (publicado=false), delega ao OutboxPublisher e marca como publicado. Limpeza diária às 03:00 (janela de 7 dias)."
                    technology "Java · @Scheduled · @Transactional"
                    tags "Application"
                }
                lRelayPub = component "OutboxPublisher" {
                    description "Bean separado do OutboxRelay para que o Spring AOP aplique @CircuitBreaker e @Retry corretamente (self-invocation bypassa o proxy). Publica no RabbitMQ com publisher confirm."
                    technology "RabbitTemplate · @CircuitBreaker(rabbit-publisher) · @Retry(rabbit-publisher)"
                    tags "AdapterOut"
                }
            }

            # ── Serviço de Consolidação Diária ─────────────────────────────
            consolidado = container "Serviço de Consolidação Diária" {
                description "Consome eventos e mantém saldo pré-computado por data com cache-aside Redis (TTL 1h). Suporta 50 req/s com até 5% de perda (NFR-02). Idempotência via tabela lancamentos_aplicados."
                technology "Spring Boot 3.5 · Java 21 · Event consumer · REST API · PostgreSQL · Redis · OTEL SDK"
                tags "Service"

                # Adapters IN — REST
                cCtrl = component "ConsolidacaoController" {
                    description "Adapter REST IN. Implementa ConsolidacaoApi gerada pelo OpenAPI Generator. Expõe GET /saldo/{data} e GET /saldo (período). Autoriza apenas GESTOR e ADMIN."
                    technology "Spring MVC · @RestController · SecurityFilterChain"
                    tags "AdapterIn"
                }
                cAdminCtrl = component "AdminController" {
                    description "Adapter REST IN. Expõe POST /admin/reconstruir para reconstrução catastrófica do saldo consolidado. Autoriza apenas ADMIN."
                    technology "Spring MVC · @RestController"
                    tags "AdapterIn"
                }

                # Adapters IN — Messaging
                cConsumer = component "LancamentoEventoConsumer" {
                    description "Adapter Messaging IN. @RabbitListener na fila consolidacao.lancamentos. Anti-Corruption Layer: converte String → TipoMovimento. @CircuitBreaker(rabbit-consumer) protege o banco de falhas em cascata."
                    technology "Spring AMQP · @RabbitListener · @CircuitBreaker"
                    tags "AdapterIn"
                }
                # Application — Use Cases
                cProcSvc = component "ProcessarLancamentoService" {
                    description "Verifica idempotência via LancamentosAplicadosRepository antes de aplicar o crédito/débito. Persiste saldo + registra em lancamentos_aplicados na mesma transação. @CacheEvict após commit."
                    technology "Java · @Service · @Transactional"
                    tags "Application"
                }
                cBusSvc = component "BuscarConsolidadoService" {
                    description "Busca saldo consolidado por data. Retorna Optional — o controller traduz para 200 ou 404."
                    technology "Java · @Service"
                    tags "Application"
                }
                cPerSvc = component "BuscarConsolidadoPeriodoService" {
                    description "Busca saldos consolidados em um intervalo de datas. Valida que data_fim >= data_inicio."
                    technology "Java · @Service"
                    tags "Application"
                }
                cRecJob = component "ReconciliacaoDiariaJob" {
                    description "@Scheduled(cron='0 0 2 * * *'): consulta totais do serviço de Lançamentos via LancamentosGateway e compara com saldo_consolidado. Divergências incrementam saldo_reconciliado_divergencias_total."
                    technology "Java · @Scheduled · Micrometer"
                    tags "Application"
                }
                cRecSvc = component "ReconstruirConsolidadoService" {
                    description "Reconstrói o saldo consolidado dia a dia consultando o serviço de Lançamentos. Permite recuperação catastrófica sem restauração de backup — o consolidado é um read model."
                    technology "Java · @Service · @Transactional"
                    tags "Application"
                }

                # Adapters OUT — Persistência e Cache
                cRepo = component "SaldoConsolidadoRepositoryAdapter" {
                    description "Adapter JPA + Redis OUT. @Cacheable(key=#data, TTL 1h): Redis HIT retorna em microssegundos; MISS consulta PostgreSQL e popula cache. RedisFallbackCacheErrorHandler silencia falhas do Redis e cai para o banco."
                    technology "Spring Data JPA · @Cacheable · @CacheEvict · Redis 7"
                    tags "AdapterOut"
                }
                cAplicadosRepo = component "LancamentosAplicadosRepositoryAdapter" {
                    description "Adapter JPA OUT. Implementa LancamentosAplicadosRepository (port). Garante idempotência do consumer: existePorId() antes de aplicar e registrar() na mesma transação do saldo."
                    technology "Spring Data JPA · PostgreSQL 16"
                    tags "AdapterOut"
                }
                cGateway = component "LancamentosGatewayAdapter" {
                    description "Adapter REST OUT. RestClient.Builder injetado pelo Spring para propagar instrumentação OTEL. Chama GET /registros/resumo no serviço de Lançamentos para reconciliação e reconstrução."
                    technology "Spring RestClient · OTEL propagation"
                    tags "AdapterOut"
                }
            }

            broker = container "Message Broker" {
                description "Desacopla Lançamentos de Consolidação via fila durável com DLQ. Garante at-least-once delivery."
                technology "RabbitMQ 3.13 · AMQP · topic exchange"
                tags "Broker"
            }

            dbLancamentos = container "PostgreSQL (Lançamentos)" {
                description "Banco do Serviço de Lançamentos. Append-only, NUMERIC(15,2), UUID. Tabelas: lancamentos, outbox, audit_log. ADR-012."
                technology "PostgreSQL 16"
                tags "Database"
            }

            dbConsolidado = container "PostgreSQL (Consolidação)" {
                description "Banco do Serviço de Consolidação. Read model + idempotência. Tabelas: saldo_consolidado, lancamentos_aplicados. ADR-012."
                technology "PostgreSQL 16"
                tags "Database"
            }

            cache = container "Redis" {
                description "Cache de saldos por data (saldo:{YYYY-MM-DD}). TTL 1h, invalidação ativa pós-evento (@CacheEvict). Fallback transparente para PostgreSQL em caso de falha. ADR-012."
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

        frontend -> idp "Redireciona para login / troca código por token" "OAuth2 Authorization Code + PKCE"
        pdv -> idp "Obtém access token" "OAuth2 Client Credentials"

        frontend -> gateway "Envia requisições autenticadas" "REST/HTTPS · Bearer JWT"
        pdv -> gateway "Envia lançamentos via API" "REST/HTTPS · Bearer JWT"

        gateway -> idp "Obtém chaves públicas (JWKS) — cache local, uma vez por restart" "HTTPS"
        gateway -> lancamentos "Roteia requisições de lançamento" "REST/HTTP · Bearer JWT passthrough"
        gateway -> consolidado "Roteia requisições de saldo" "REST/HTTP · Bearer JWT passthrough"

        lancamentos -> idp "Valida JWT via JWKS (oauth2-resource-server)" "HTTPS · cache local"
        consolidado -> idp "Valida JWT via JWKS (oauth2-resource-server)" "HTTPS · cache local"

        lancamentos -> dbLancamentos "Persiste lançamentos e outbox (mesma tx)" "SQL/TLS"
        lancamentos -> otelCollector "Envia traces, métricas e logs" "OTLP gRPC"

        outboxRelay -> dbLancamentos "Faz polling da tabela outbox" "SQL/TLS"
        outboxRelay -> broker "Publica eventos com trace_id" "AMQP · publisher confirm"
        outboxRelay -> otelCollector "Envia traces e logs" "OTLP gRPC"

        broker -> consolidado "Entrega eventos (at-least-once)" "AMQP"
        consolidado -> dbConsolidado "Upsert idempotente de saldo" "SQL/TLS"
        consolidado -> cache "Lê saldo e invalida após evento" "Redis Protocol"
        consolidado -> lancamentos "GET /registros/resumo (reconciliação diária e reconstrução catastrófica)" "REST/HTTP"
        consolidado -> otelCollector "Envia traces, métricas e logs" "OTLP gRPC"

        # ── Relacionamentos — Componentes Lançamentos ─────────────────────────
        lCtrl -> lRegSvc "registrar(Command)" ""
        lCtrl -> lBusSvc "executar(LancamentoId)" ""
        lCtrl -> lLstSvc "executar(Query)" ""
        lCtrl -> lEstSvc "executar(Command)" ""
        lCtrl -> lResSvc "executar(data)" ""

        lRegSvc -> lRepo "existePorId · salvar"
        lRegSvc -> lOutboxRepo "salvar (mesma transação)"
        lRegSvc -> lAuditPub "publicar(AuditEvento)"
        lEstSvc -> lRepo "buscarPorId · salvar · atualizarEstornado"
        lEstSvc -> lOutboxRepo "salvar (mesma transação)"
        lEstSvc -> lAuditPub "publicar(AuditEvento)"
        lBusSvc -> lRepo "buscarPorId"
        lLstSvc -> lRepo "listarPorData"
        lResSvc -> lRepo "somarPorData"

        lAuditPub -> lAuditListener "ApplicationEvent (Spring)"
        lAuditListener -> dbLancamentos "INSERT audit_log (AFTER_COMMIT + @Async)"

        lRepo -> dbLancamentos "SQL"
        lOutboxRepo -> dbLancamentos "SQL"

        lRelayJob -> lRelayPub "publicar(payload, lancamentoId)"
        lRelayJob -> dbLancamentos "SELECT outbox WHERE publicado=false · UPDATE publicado=true"
        lRelayPub -> broker "RabbitTemplate.convertAndSend"

        # ── Relacionamentos — Componentes Consolidado ─────────────────────────
        cCtrl -> cBusSvc "executar(data)"
        cCtrl -> cPerSvc "executar(inicio, fim)"
        cAdminCtrl -> cRecSvc "executar(inicio, fim)"

        cConsumer -> cProcSvc "executar(Command)"

        cProcSvc -> cAplicadosRepo "existePorId · registrar"
        cProcSvc -> cRepo "buscarOuCriar · salvar"
        cBusSvc -> cRepo "buscarPorData"
        cPerSvc -> cRepo "listarPorPeriodo"
        cRecJob -> cGateway "buscarResumo(data)"
        cRecJob -> cRepo "buscarPorData"
        cRecJob -> prometheus "saldo_reconciliado_divergencias_total++"
        cRecSvc -> cGateway "buscarResumo(data)"
        cRecSvc -> cRepo "salvar"

        cRepo -> dbConsolidado "SQL"
        cRepo -> cache "Redis GET · SET · DEL"
        cAplicadosRepo -> dbConsolidado "SQL"
        cGateway -> lancamentos "GET /registros/resumo" "REST/HTTP"

        # ── Relacionamentos — Observabilidade Interna ─────────────────────────
        idp -> promtail "Emite logs de eventos de segurança" "Docker stdout"
        idp -> prometheus "Expõe métricas JVM e de autenticação" "HTTP /metrics (pull)"

        otelCollector -> tempo "Exporta traces" "OTLP"
        otelCollector -> prometheus "Exporta métricas" "remote write"
        otelCollector -> loki "Exporta logs (pós-redação PII)" "push"

        promtail -> loki "Envia logs Docker (pós-redação PII)" "push"

        prometheus -> alertmanager "Dispara alertas" "HTTP"

        blackbox -> gateway "Probe HTTP /ping" "HTTP"
        blackbox -> idp "Probe HTTP /health/ready" "HTTP"
        blackbox -> grafana "Probe HTTP /api/health" "HTTP"
        blackbox -> prometheus "Probe HTTP /-/healthy" "HTTP"
        blackbox -> loki "Probe HTTP /ready" "HTTP"
        blackbox -> tempo "Probe HTTP /ready" "HTTP"

        grafana -> prometheus "Query métricas" "PromQL"
        grafana -> loki "Query logs" "LogQL"
        grafana -> tempo "Query traces" "TraceQL"
        grafana -> pyroscope "Query profiles" "HTTP"
        grafana -> alertmanager "Lê alertas e silences" "HTTP"

        # ── Deployment — Desenvolvimento Local (Docker Compose) ───────────────
        devEnv = deploymentEnvironment "Desenvolvimento" {
            devMachine = deploymentNode "Máquina do Desenvolvedor" {
                technology "Docker Compose · Linux / macOS"

                traefikNode = deploymentNode "traefik" {
                    technology "Traefik 3 · :8090 :8443 :8091"
                    containerInstance gateway
                }

                keycloakNode = deploymentNode "keycloak" {
                    technology "Keycloak 26 · :8180"
                    infrastructureNode "Keycloak" {
                        description "Identity Provider local. Realm fluxocaixa com roles caixa/gestor/admin/pdv e scope basic (oidc-sub-mapper)."
                        technology "Keycloak 26"
                    }
                }

                lancamentosNode = deploymentNode "lancamentos" {
                    technology "JVM · Spring Boot 3.5 · :8080"
                    containerInstance lancamentos
                }

                outboxRelayNode = deploymentNode "outbox-relay" {
                    technology "JVM · Spring Boot 3.5 (mesmo JAR)"
                    containerInstance outboxRelay
                }

                consolidadoNode = deploymentNode "consolidado" {
                    technology "JVM · Spring Boot 3.5 · :8081"
                    containerInstance consolidado
                }

                postgresLancNode = deploymentNode "postgres-lancamentos" {
                    technology "PostgreSQL 16 · rede lancamentos-data (internal)"
                    containerInstance dbLancamentos
                }

                postgresConsNode = deploymentNode "postgres-consolidado" {
                    technology "PostgreSQL 16 · rede consolidacao-data (internal)"
                    containerInstance dbConsolidado
                }

                redisNode = deploymentNode "redis" {
                    technology "Redis 7 · AOF · rede consolidacao-data (internal)"
                    containerInstance cache
                }

                rabbitmqNode = deploymentNode "rabbitmq" {
                    technology "RabbitMQ 3.13 · :15672 Management · rede messaging (internal)"
                    containerInstance broker
                }

                obsNode = deploymentNode "Stack de Observabilidade" {
                    technology "Docker Compose · rede observability (internal)"

                    deploymentNode "otel-collector" {
                        technology "OTEL Collector Contrib · :4317"
                        containerInstance otelCollector
                    }
                    deploymentNode "prometheus" {
                        technology "Prometheus 3.1 · :9090"
                        containerInstance prometheus
                    }
                    deploymentNode "loki" {
                        technology "Loki 3.3 · :3100"
                        containerInstance loki
                    }
                    deploymentNode "tempo" {
                        technology "Tempo 2.6 · :3200"
                        containerInstance tempo
                    }
                    deploymentNode "grafana" {
                        technology "Grafana 11.4 · :3000"
                        containerInstance grafana
                    }
                }
            }
        }

        # ── Deployment — Produção (AWS) ────────────────────────────────────────
        prodEnv = deploymentEnvironment "Produção" {
            awsCloud = deploymentNode "AWS" {
                technology "Amazon Web Services"

                region = deploymentNode "us-east-1" {
                    technology "AWS Region"

                    cloudfront = deploymentNode "CloudFront + API Gateway HTTP API" {
                        technology "CloudFront · WAF v2 · API Gateway HTTP API"
                        containerInstance gateway
                    }

                    eksCluster = deploymentNode "EKS Cluster" {
                        technology "Amazon EKS · Kubernetes 1.31"

                        lancPod = deploymentNode "lancamentos (Deployment)" {
                            technology "Pod · JVM · Spring Boot 3.5"
                            instances 2
                            containerInstance lancamentos
                        }

                        outboxPod = deploymentNode "outbox-relay (Deployment)" {
                            technology "Pod · JVM · Spring Boot 3.5"
                            instances 1
                            containerInstance outboxRelay
                        }

                        consPod = deploymentNode "consolidado (Deployment)" {
                            technology "Pod · JVM · Spring Boot 3.5 · HPA: 2-10 réplicas"
                            instances 3
                            containerInstance consolidado
                        }
                    }

                    rdsLanc = deploymentNode "RDS (Lançamentos)" {
                        technology "Amazon RDS · PostgreSQL 16 · Multi-AZ · RDS Proxy"
                        containerInstance dbLancamentos
                    }

                    rdsCons = deploymentNode "RDS (Consolidação)" {
                        technology "Amazon RDS · PostgreSQL 16 · Multi-AZ · RDS Proxy"
                        containerInstance dbConsolidado
                    }

                    elastiCache = deploymentNode "ElastiCache" {
                        technology "Amazon ElastiCache · Redis 7 · cluster mode · KMS CMK"
                        containerInstance cache
                    }

                    amazonMQ = deploymentNode "Amazon MQ" {
                        technology "Amazon MQ · RabbitMQ 3.13 · AMQPS · Multi-AZ"
                        containerInstance broker
                    }

                    cognitoNode = deploymentNode "Cognito / IdP Corporativo" {
                        technology "AWS Cognito ou IdP corporativo"
                        infrastructureNode "Identity Provider" {
                            description "Emite JWTs RS256, expõe JWKS. Substitui Keycloak local em produção. ADR-014."
                            technology "AWS Cognito"
                        }
                    }

                    obsStack = deploymentNode "Stack de Observabilidade" {
                        technology "EKS · rede privada"

                        deploymentNode "otel-collector" {
                            technology "Pod · OTEL Collector Contrib"
                            containerInstance otelCollector
                        }
                        deploymentNode "prometheus" {
                            technology "Pod · Prometheus 3.1"
                            containerInstance prometheus
                        }
                        deploymentNode "loki" {
                            technology "Pod · Loki 3.3"
                            containerInstance loki
                        }
                        deploymentNode "tempo" {
                            technology "Pod · Tempo 2.6"
                            containerInstance tempo
                        }
                        deploymentNode "grafana" {
                            technology "Pod · Grafana 11.4"
                            containerInstance grafana
                        }
                    }
                }
            }
        }
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

        component lancamentos "lancamentos-components" {
            title "Components — Serviço de Lançamentos (Hexagonal Architecture)"
            include *
            autoLayout
        }

        component consolidado "consolidado-components" {
            title "Components — Serviço de Consolidação Diária (Hexagonal Architecture)"
            include *
            autoLayout
        }

        component outboxRelay "outbox-relay-components" {
            title "Components — Outbox Relay"
            include *
            autoLayout
        }

        deployment sistema "Desenvolvimento" "deployment-dev" {
            title "Deployment — Docker Compose (desenvolvimento local)"
            include *
            autoLayout
        }

        deployment sistema "Produção" "deployment-prod" {
            title "Deployment — AWS (produção)"
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
            element "AdapterIn" {
                background "#ca8a04"
                color "#ffffff"
                shape "RoundedBox"
            }
            element "Application" {
                background "#16a34a"
                color "#ffffff"
                shape "RoundedBox"
            }
            element "AdapterOut" {
                background "#db2777"
                color "#ffffff"
                shape "RoundedBox"
            }
        }

        themes theme.json

    }

}
