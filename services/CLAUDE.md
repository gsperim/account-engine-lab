# CLAUDE.md — services/

Navegador da implementação. Para contexto do projeto, estado atual e pendências: ver [CLAUDE.md raiz](../CLAUDE.md).

## Arquitetura interna (ambos os serviços)

Arquitetura Hexagonal + DDD Tático. Estrutura de pacotes idêntica nos dois serviços:

```
domain/
  model/          → Aggregates, Value Objects, Enums (Java puro, zero Spring)
  exception/      → Domain exceptions
  port/in/        → Use case interfaces (contratos de entrada)
  port/out/       → Repository/publisher interfaces (contratos de saída)

application/usecase/   → Implementações dos use cases (sem anotações Spring)

adapter/in/rest/       → Controllers Spring MVC (implementam interface gerada pelo OpenAPI Generator)
adapter/in/messaging/  → RabbitMQ consumers (só no consolidado)
adapter/out/persistence/ → JPA adapters, Redis cache config
adapter/out/messaging/   → RabbitMQ publisher (só em lançamentos)
```

> Os controllers **não são escritos à mão** — implementam a interface `*Api` gerada a partir do contrato OpenAPI em `contracts/openapi/`. Editar o contrato regenera a interface na build.

---

## Mapa de rastreabilidade

### ADR-002 (Message Broker — RabbitMQ) + ADR-003 (Outbox Pattern)
| Artefato | Arquivo |
|---|---|
| Publicação direta (testes) | `lancamentos/.../adapter/out/messaging/RabbitEventoPublisher.java` |
| Retry + Circuit Breaker | `OutboxRelay.java` — `@CircuitBreaker` + `@Retry(rabbit-publisher)` |
| Persistência atômica | `lancamentos/.../adapter/out/messaging/OutboxRelay.java` + `OutboxRepositoryAdapter.java` |
| Entidade outbox | `lancamentos/.../adapter/out/persistence/OutboxJpaEntity.java` |
| Consumer + ACL | `consolidado/.../adapter/in/messaging/LancamentoEventoConsumer.java` |
| Circuit Breaker consumer | `LancamentoEventoConsumer.java` — `@CircuitBreaker(rabbit-consumer)` |
| Testes | `RabbitEventoPublisherTest.java` · `LancamentoEventoConsumerTest.java` |

### ADR-012 (Persistência — database per service)
| Artefato | Arquivo |
|---|---|
| Banco lançamentos | `lancamentos/.../adapter/out/persistence/LancamentoRepositoryAdapter.java` |
| Banco consolidado | `consolidado/.../adapter/out/persistence/SaldoConsolidadoRepositoryAdapter.java` |
| Cache Redis + fallback | `SaldoConsolidadoRepositoryAdapter.java` — `@Cacheable` + `CacheErrorHandler` |
| Config Redis | `consolidado/.../adapter/out/persistence/CacheConfig.java` |
| Migrations | `lancamentos/src/main/resources/db/migration/` · `consolidado/src/main/resources/db/migration/` |
| Testes | `LancamentoRepositoryAdapterTest.java` (`@DataJpaTest`) · `SaldoConsolidadoRepositoryAdapterTest.java` |

### ADR-017 (Spec-Driven Development — OpenAPI Generator)
| Artefato | Arquivo |
|---|---|
| Contrato lançamentos | `contracts/openapi/lancamentos.yaml` |
| Contrato consolidado | `contracts/openapi/consolidado.yaml` |
| Interface gerada (build) | `build/generated/.../LancamentosApi.java` · `ConsolidacaoApi.java` |
| Implementação | `LancamentoController.java` · `ConsolidacaoController.java` |
| Build config | `lancamentos/build.gradle` · `consolidado/build.gradle` (plugin `openapi-generator`) |

### Idempotência (NFR crítico)
| Artefato | Arquivo |
|---|---|
| UUID = `Idempotency-Key` HTTP | `lancamentos/.../domain/model/LancamentoId.java` |
| Garantia PK única → 409 | `LancamentoDuplicadoException.java` + `GlobalExceptionHandler.java` |
| Teste | `LancamentoControllerTest.java` — verifica POST idempotente |

### Resiliência — NFR crítico (lançamentos não pode cair se consolidado cair)
| Mecanismo | Onde |
|---|---|
| Outbox garante entrega mesmo com RabbitMQ fora | `OutboxRelay.java` + migration V2 |
| Retry publisher (3×, backoff exponencial) | `application.properties` — `resilience4j.retry.rabbit-publisher` |
| Circuit Breaker publisher (50%, 30s) | `application.properties` — `resilience4j.circuitbreaker.rabbit-publisher` |
| Circuit Breaker consumer | `application.properties` — `resilience4j.circuitbreaker.rabbit-consumer` |
| Redis fallback para banco | `CacheConfig.java` — `CacheErrorHandler` silencia falhas Redis |

---

## Pirâmide de testes

```
                    [Integração]
             @SpringBootTest (H2 in-memory)
           LancamentosApplicationTests (1)
         ConsolidadoApplicationTests (1)

          [Slice — adapter REST]
       @WebMvcTest — sem Spring completo
    LancamentoControllerTest · ConsolidacaoControllerTest

       [Slice — adapter persistence]
      @DataJpaTest — só JPA, H2 in-memory
 LancamentoRepositoryAdapterTest · SaldoConsolidadoRepositoryAdapterTest

          [Unitários — application]
      JUnit 5 + Mockito (ports mockados)
  RegistrarLancamentoServiceTest · BuscarLancamentoServiceTest · ...

       [Unitários — domain]
       JUnit 5 + AssertJ (Java puro)
    LancamentoTest · ValorTest · SaldoConsolidadoTest
```

**Total: 106 testes** — lançamentos + consolidado (cresceu com handlers no GlobalExceptionHandler).

---

## Decisões de implementação não óbvias

- **`TipoMovimento` no consolidado é um tipo próprio**, não importado de lançamentos — bounded context deliberadamente separado. O `LancamentoEventoConsumer` faz a tradução (Anti-Corruption Layer).
- **Outbox polling via `@Scheduled`** — relay em container separado (`outbox-relay`). A publicação usa `OutboxPublisher` (componente distinto de `OutboxRelay`) para que o Spring AOP aplique `@CircuitBreaker` + `@Retry` corretamente — self-invocation bypassa o proxy.
- **Virtual Threads habilitadas** (`spring.threads.virtual.enabled=true`) — alinha com o NFR de throughput do consolidado (50 req/s).
- **OpenAPI Generator na build** — qualquer mudança no contrato `.yaml` quebra a compilação se o controller não implementar a nova interface. Essa é a intenção: contratos são lei.
- **`@WebMvcTest` + Spring Security**: não usar `@EnableMethodSecurity` (quebra detecção de `@RequestMapping` em interfaces CGLIB-proxied). Regras de autorização ficam no `SecurityFilterChain`. Nos testes: `@Import(SecurityConfig.class)` + `@MockitoBean JwtDecoder` + `SecurityMockMvcRequestPostProcessors.jwt()`. Paths nos testes devem ser os do contrato (`/registros`, `/saldo`), não os prefixos do Traefik.
- **`operadorId` vem do claim `sub` do JWT** (UUID Keycloak) — fallback para `preferred_username` se ausente. Extraído via `SecurityContextHolder` no `LancamentoController`.
- **Logging estruturado via SLF4J 2.0 fluent API** — `addKeyValue("event", ...)` + `.setCause(t)`. Contexto de sessão (http_method, http_path, correlation_id) via MDC no `LoggingContextFilter` / `MessagingLogContextAspect`; por-evento via `addKeyValue`. Não usar `logstash-logback-encoder` (exigiria logback XML).
- **`RestClient.Builder` injetado** — `LancamentosGatewayAdapter` recebe `RestClient.Builder` via DI. Instanciar `RestClient.builder()` diretamente (estático) desliga a instrumentação do Micrometer e quebra a propagação de trace.
- **`MessagingLogContextAspect`** — AOP em `@RabbitListener` apenas para ciclo de vida do MDC (salva/restaura contexto). Não logar entrada/saída de método — gera ruído. Consumers sem try/finally de MDC.

### Rastreabilidade — Observabilidade (logging estruturado + tracing)
| Artefato | Arquivo |
|---|---|
| MDC por request HTTP | `lancamentos/.../adapter/in/rest/LoggingContextFilter.java` · `consolidado/.../adapter/in/rest/LoggingContextFilter.java` |
| MDC por consumer RabbitMQ | `consolidado/.../adapter/in/messaging/MessagingLogContextAspect.java` — `@Around @RabbitListener` |
| Propagação trace RabbitMQ | `application.properties` — `spring.rabbitmq.listener.simple.observation-enabled=true` (consolidado) + `spring.rabbitmq.template.observation-enabled=true` (lancamentos) |
| Propagação trace HTTP outbound | `LancamentosGatewayAdapter` — `RestClient.Builder` injetado pelo Spring |
| Promtail pipeline | `observability/promtail.yml` — extrai `event`, `trace_id`, `span_id`, `correlation_id`; output restrito a serviços Spring |

### Rastreabilidade — Segurança (ADR-004 / ADR-014)
| Artefato | Arquivo |
|---|---|
| Configuração Spring Security | `lancamentos/.../adapter/in/rest/SecurityConfig.java` · `consolidado/.../adapter/in/rest/SecurityConfig.java` |
| Matriz de autorização por role | `SecurityConfig.securityFilterChain()` — `hasAnyRole("CAIXA","PDV")` etc. |
| Extração de operadorId do JWT | `LancamentoController.registrarLancamento()` — `jwt.getSubject()` |
| Realm Keycloak | `keycloak/realm-fluxocaixa.json` — scope `basic` com `oidc-sub-mapper` (KC 25+) |
| Rota Keycloak no Traefik | `docker-compose.yml` labels `keycloak` + `traefik/dynamic/middlewares.yml` `strip-auth` |
| Auth no k6 | `tests/k6/auth.js` — ROPC, cache per-VU, retry no boot |

### Cobertura de código — JaCoCo
| Artefato | Detalhe |
|---|---|
| Plugin | `id 'jacoco'` em ambos os `build.gradle` |
| Geração | `jacocoTestReport` configurado como `finalizedBy` da task `test` |
| Exclusões | `**/generated/**` (código OpenAPI Generator) excluído do relatório |
| Reports | HTML em `build/reports/jacoco/test/html/`; XML em `build/reports/jacoco/test/` (para SonarCloud futuro) |
| CI artifacts | `coverage-lancamentos` · `coverage-consolidado` — retenção 30 dias |
| Pages | Publicados em `/coverage/lancamentos/` · `/coverage/consolidado/` no GitHub Pages |

### Rastreabilidade — Audit Log (NFR-09 / ISO 37301)
| Artefato | Arquivo |
|---|---|
| Domain model | `lancamentos/.../domain/model/AuditEvento.java` — record puro, sem dependência Spring |
| Port | `lancamentos/.../domain/port/out/AuditPublisher.java` — interface hexagonal |
| Adapter (publicação) | `lancamentos/.../adapter/out/audit/AuditPublisherAdapter.java` — publica Spring `ApplicationEvent` |
| Listener (persistência) | `lancamentos/.../adapter/out/audit/AuditEventListener.java` — `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` |
| Entidade JPA | `lancamentos/.../adapter/out/persistence/AuditLogJpaEntity.java` |
| Repositório | `lancamentos/.../adapter/out/persistence/AuditLogJpaRepository.java` |
| Migration | `lancamentos/src/main/resources/db/migration/V6__create_audit_log.sql` |
| Testes | `AuditEventListenerTest.java` — persistência correta + falha silenciosa |

**Decisão não-óbvia:** o listener usa `AFTER_COMMIT` + `@Async` — o response HTTP é enviado antes do audit ser gravado. Falha no audit é capturada e logada, nunca propagada. Com Virtual Threads habilitado, Spring Boot usa virtual thread executor para `@Async` sem config extra.

---

## Estado da implementação — pré-Etapa 9

**106+ testes verdes**. Todos os endpoints implementados e cobertos. Audit log ativo em lançamentos.

| Serviço | Artefatos relevantes (2026-05-18) |
|---------|--------------------------------------|
| lancamentos | `AuditEventListener`, `AuditPublisherAdapter`, `AuditLogJpaEntity`, `V6__create_audit_log.sql`, logs refatorados |
| consolidado | `MessagingLogContextAspect`, `LoggingContextFilter`, `LancamentosGatewayAdapter` (RestClient.Builder) |

Pendente para versões futuras: audit no consolidado (admin ops), backoffice de DLQ.
