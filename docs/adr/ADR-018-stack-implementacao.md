---
tags:
  - adr
  - decisao
  - engenharia
  - stack
---

# ADR-018 — Stack de Implementação: Java 21 com Spring Boot 3.5

**Papéis:** 🛠️ Engenheiro de Software · ⚙️ Arquiteto de Tecnologia · 🧩 Arquiteto de Soluções  
**Data:** 2026-05-09  
**Status:** Aceito  
**Requisitos:** [NFR-01 — Disponibilidade](../negocio/requisitos.md#nfr-01) · [NFR-02 — Throughput](../negocio/requisitos.md#nfr-02)

---

## Contexto

Na Etapa 7, iniciamos a implementação dos dois microserviços — `lancamentos` e `consolidado`. A decisão de linguagem e framework define o ecossistema de bibliotecas, a curva de onboarding do time, a compatibilidade com ferramentas de observabilidade já adotadas (Micrometer, OTEL) e a aderência ao padrão de mercado do setor financeiro brasileiro.

Três combinações foram consideradas:

| Alternativa | Linguagem | Framework |
|---|---|---|
| A | Python 3 | FastAPI |
| B | Node.js | NestJS |
| C ✓ | Java 21 | Spring Boot 3.5 |
| D | Java 21 | Quarkus 3 |

---

## Decisão

Adotar **Java 21 com Spring Boot 3.5** como stack de implementação dos microserviços.

---

## Java frente a Python e Node.js

### Por que não Python (FastAPI)

Python é a língua franca de ciência de dados e ML — não de sistemas transacionais financeiros. Os trade-offs são desfavoráveis para este contexto:

- **GIL (Global Interpreter Lock):** limita paralelismo real de threads. Contorno via `asyncio` funciona para I/O, mas penaliza workloads de CPU (serialização JSON em volume, validação) com overhead de event loop.
- **Tipagem dinâmica:** FastAPI usa Pydantic para validação em runtime. Erros de contrato que Java detecta em tempo de compilação surgem em Python apenas em produção — incompatível com o nível de confiabilidade esperado de um sistema financeiro.
- **Ecossistema de persistência:** SQLAlchemy e Alembic são funcionais, mas não têm a maturidade de JPA + Hibernate para transações complexas e mapeamentos relacionais sofisticados.
- **Padrão de mercado:** bancos brasileiros (Itaú, Bradesco, C6, Nubank) usam Python em camadas analíticas, não em serviços transacionais de missão crítica.

### Por que não Node.js (NestJS)

Node.js tem adoção crescente em APIs de borda e BFFs, mas apresenta limitações estruturais para o perfil deste sistema:

- **Event loop single-thread:** o modelo não-bloqueante é eficiente para I/O, mas um erro de código bloqueante (ex.: loop CPU-bound em operação de cálculo de saldo) paralisa todo o processo.
- **Tipagem opcional:** TypeScript mitiga, mas não elimina o risco — o runtime ainda é JavaScript. A garantia de tipos do Java é verificada pelo compilador, não por uma camada adicional.
- **Ecossistema financeiro:** ORM (TypeORM, Prisma) e mensageria (amqplib) têm menos história em produção de alta escala financeira do que JPA + Spring AMQP.
- **O próprio mercado diz:** o time de avaliação do Banco Carrefour espera Spring — divergir aumenta o risco de avaliação negativa sem ganho funcional equivalente.

### Por que Java

- **Tipagem estática + compilador como guarda:** erros de contrato entre camadas são detectados antes do deploy. Em um sistema com Outbox Pattern, idempotência e contratos OpenAPI, esse guarda é crítico.
- **JVM madura:** GC tuning (G1, ZGC), JIT compilation, e Java 21 Virtual Threads (Project Loom) eliminam a vantagem histórica de Node em I/O concorrente.
- **Padrão do setor financeiro brasileiro:** a esmagadora maioria dos bancos, fintechs e processadoras de pagamento no Brasil operam backends Java. Isso garante disponibilidade de mão de obra, bibliotecas auditadas e referências de operação em escala.

---

## Spring Boot frente ao Quarkus

### O que Quarkus oferece

Quarkus é otimizado para compilação nativa via GraalVM — startup em milissegundos e footprint de memória menor. Suas vantagens são reais:

- Startup ~50ms vs ~3s do Spring Boot no JVM padrão
- RSS de memória ~60-80 MB nativamente vs ~150-200 MB JVM
- Suporte a MicroProfile (métricas, health, tracing com interface padronizada)
- Panache simplifica JPA com Active Record pattern

### Por que Spring Boot

**1. O gap fecha com containers + virtual threads**

Nosso ambiente de execução é container Docker/Kubernetes — não FaaS/Lambda onde startup frio é crítico. Um container Spring Boot inicializado em ~3s num pod Kubernetes é reiniciado uma vez. O custo de startup é amortizado em horas de operação. Com Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`), o throughput de I/O concorrente se equipara ao modelo reativo sem a complexidade do Reactor.

**2. Ecossistema de primeira classe para o que usamos**

Cada componente da stack já tem integração nativa e madura no Spring:

| Componente | Spring Boot | Quarkus |
|---|---|---|
| RabbitMQ | `spring-amqp` — 15 anos em produção | Quarkus Messaging (SmallRye) — funcional, menos referências financeiras |
| JPA / Flyway | `spring-data-jpa` + `flyway-core` — integração automática | Hibernate ORM + Flyway — requer mais configuração manual |
| Observabilidade | Micrometer + Actuator — já na stack (ADR-015) | MicroProfile Metrics — requer adaptação para nosso stack PLT |
| OpenAPI Generator | Gerador `spring` — interface direta com anotações MVC (ADR-017) | Gerador `jaxrs` — diferente das anotações `@RequestMapping` do gerador |
| Segurança JWT | `spring-security-oauth2-resource-server` | Quarkus OIDC — funcional, mas Keycloak integration é melhor documentada com Spring |

**3. Coerência com o ADR-017 (SDD)**

O OpenAPI Generator com configuração `spring` gera interfaces com anotações `@RequestMapping`, `@RestController`, `@Valid` — idiomáticas para Spring MVC. Migrar para Quarkus exigiria reescrever o gerador para JAX-RS, quebrando a cadeia spec → interface → controller que já está validada.

**4. Talent pool e avaliação**

A audiência do desafio é o Banco Carrefour — uma instituição financeira com time predominantemente Java/Spring. Entregar Quarkus é uma escolha defensável tecnicamente, mas cria fricção desnecessária na avaliação de quem vai revisar o código.

---

## Alternativas rejeitadas

| Alternativa | Motivo da rejeição |
|---|---|
| Python + FastAPI | GIL, tipagem dinâmica, ecossistema de persistência menos maduro para contexto financeiro |
| Node.js + NestJS | Event loop single-thread, runtime JavaScript, menor história em escala financeira |
| Java + Quarkus | Startup frio irrelevante em containers, ecossistema menos integrado com nossa stack (AMQP, Micrometer, SDD), sem vantagem operacional para este perfil de workload |
| Java + Micronaut | Mesma análise do Quarkus, com adoção ainda menor no mercado financeiro brasileiro |

---

## Trade-offs aceitos

| Trade-off | Mitigação |
|---|---|
| Startup ~3s (JVM) vs ~50ms (Quarkus nativo) | Irrelevante em containers long-running; Kubernetes readiness probe cobre o gap |
| Footprint de memória maior (~180 MB/container) | Aceitável para o perfil de carga (2 serviços, infra dedicada); ZGC reduz pauses |
| Verbosidade do Java vs Python/Node | Compensado por segurança de tipos, tooling de IDE e manutenibilidade de longo prazo |

---

## Consequências

- Buildpacks e imagens Docker baseados em `eclipse-temurin:21-jre-alpine`
- `spring.threads.virtual.enabled=true` habilitado para aproveitar Project Loom no Spring Boot 3.2+
- Todos os serviços compartilham o mesmo gerador OpenAPI (`spring`) e seguem o padrão SDD do ADR-017
- A stack de observabilidade (Micrometer + OTEL) já está disponível via `spring-boot-starter-actuator` sem configuração adicional
