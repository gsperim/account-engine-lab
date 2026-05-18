# Controle de Fluxo de Caixa Diário

Sistema de controle financeiro para comerciantes com dois serviços independentes e desacoplados:

- **Serviço de Lançamentos** — registra débitos e créditos em tempo real, com idempotência garantida por `Idempotency-Key`
- **Serviço de Consolidação Diária** — mantém o saldo consolidado por dia com cache Redis; suporta 50 req/s

**NFR crítico:** o registro de lançamentos nunca é interrompido por falha no serviço de consolidação. O desacoplamento é garantido por RabbitMQ + Outbox Pattern — lançamentos persistem mesmo com o broker fora do ar.

---

## Início Rápido

**Pré-requisitos:** Docker 24+ e Docker Compose v2 (plugin).

```bash
# 1. Clonar
git clone https://github.com/gsperim/account-engine-lab.git
cd account-engine-lab

# 2. Subir infraestrutura (banco, cache, mensageria, observabilidade)
docker compose up -d

# 3. Subir os serviços de aplicação
docker compose --profile app up -d

# 4. Verificar saúde
curl http://localhost:8090/lancamentos/actuator/health
curl http://localhost:8090/consolidacao/actuator/health
```

Ou via script que verifica pré-requisitos automaticamente:

```bash
bash setup.sh
```

---

## Endpoints da API

O gateway Traefik expõe tudo em `http://localhost:8090` (HTTP) e `https://localhost:8443` (HTTPS).

### Serviço de Lançamentos — `POST /lancamentos/registros`

```bash
# Registrar um crédito
curl -X POST http://localhost:8090/lancamentos/registros \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "tipo": "CREDITO",
    "valor": 1500.00,
    "descricao": "Venda balcão",
    "dataCompetencia": "2026-05-14"
  }'

# Registrar um débito
curl -X POST http://localhost:8090/lancamentos/registros \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "tipo": "DEBITO",
    "valor": 320.50,
    "descricao": "Fornecedor XYZ",
    "dataCompetencia": "2026-05-14"
  }'

# Buscar um lançamento
curl http://localhost:8090/lancamentos/registros/{id}

# Listar lançamentos por data
curl "http://localhost:8090/lancamentos/registros?data=2026-05-14&page=0&size=20"
```

### Serviço de Consolidação — `GET /consolidacao/saldos/{data}`

```bash
# Consultar saldo consolidado do dia
curl http://localhost:8090/consolidacao/saldos/2026-05-14
```

Resposta:
```json
{
  "data": "2026-05-14",
  "totalCreditos": 1500.00,
  "totalDebitos": 320.50,
  "saldo": 1179.50,
  "quantidadeLancamentos": 2,
  "atualizadoEm": "2026-05-14T10:30:00Z"
}
```

---

## Executando os Testes

```bash
# Testes de unidade e integração (~146 testes — lancamentos + consolidado)
cd services/lancamentos && ./gradlew test
cd services/consolidado && ./gradlew test

# Relatório de cobertura
./gradlew test jacocoTestReport
# Relatório em: build/reports/jacoco/test/html/index.html
```

### Testes de Carga com k6

Requer [k6](https://k6.io/docs/get-started/installation/) instalado e os serviços rodando.

```bash
cd tests/k6

# Smoke test — valida fluxo básico (30s)
k6 run smoke.js

# Load test — 50 req/s por 5 min (NFR-02)
k6 run load.js

# Stress test — rampa até 200 VUs
k6 run stress.js

# Teste de idempotência
k6 run idempotencia.js
```

---

## Serviços e Portas

| Serviço | URL | Descrição |
|---------|-----|-----------|
| **API Gateway (HTTP)** | http://localhost:8090 | Entrada da aplicação |
| **API Gateway (HTTPS)** | https://localhost:8443 | TLS (certificado auto-assinado) |
| Traefik Dashboard | http://localhost:8091 | Rotas e middlewares ativos |
| Grafana | http://localhost:3000 | Dashboards (admin/admin) |
| Prometheus | http://localhost:9090 | Métricas e alertas |
| RabbitMQ Management | http://localhost:15672 | Filas (fluxocaixa/fluxocaixa_dev) |
| Keycloak | http://localhost:8180 | Identity Provider |
| Portal de Documentação | http://localhost:8000 | Arquitetura, ADRs, decisões |
| Diagramas C4 | http://localhost:8080 | Contexto e containers (Structurizr Lite) |
| **Swagger UI** | http://localhost:8070 | Contratos OpenAPI navegáveis (Lançamentos + Consolidado) |

```bash
# Subir apenas a infraestrutura de dados
docker compose up postgres-lancamentos postgres-consolidado redis rabbitmq -d

# Subir apenas a stack de observabilidade
docker compose up prometheus grafana loki tempo otel-collector -d

# Escalar consolidado para 3 réplicas (valida NFR-02)
docker compose --profile app up --scale consolidado=3

# Acompanhar logs dos serviços
docker compose logs -f lancamentos consolidado

# Parar tudo
docker compose down

# Parar e apagar volumes (reset completo)
docker compose down -v
```

---

## Arquitetura

**Stack:** Spring Boot 3 + Java 21 · Arquitetura Hexagonal + DDD Tático · Contratos OpenAPI 3.1 (spec-first)

**Decisões-chave:**

| Problema | Solução | ADR |
|----------|---------|-----|
| Isolamento de falhas entre serviços | RabbitMQ AMQP assíncrono | ADR-002 |
| Entrega garantida mesmo com broker fora | Outbox Pattern + `@Scheduled` relay | ADR-003 |
| Performance de consulta (50 req/s) | Redis cache com fallback para banco | ADR-012 |
| Contratos como contrato de lei | OpenAPI Generator na build Gradle | ADR-017 |
| Idempotência de lançamentos | `Idempotency-Key` UUID como PK | OpenAPI spec |

**Fluxo de dados:**

```
Cliente → Traefik → Lançamentos → PostgreSQL (escrita atômica)
                              ↓
                        Outbox table → OutboxRelay (@Scheduled)
                                              ↓
                                         RabbitMQ → Consolidado → Redis + PostgreSQL
```

A documentação completa — visão executiva, ADRs, diagramas C4, decisões de segurança e observabilidade — está disponível em http://localhost:8000 após subir o portal.

Os contratos OpenAPI também podem ser explorados online via Swagger Editor, sem precisar subir o ambiente local:

- [Lançamentos](https://editor.swagger.io/?url=https://raw.githubusercontent.com/gsperim/account-engine-lab/main/contracts/openapi/lancamentos.yaml)
- [Consolidado Diário](https://editor.swagger.io/?url=https://raw.githubusercontent.com/gsperim/account-engine-lab/main/contracts/openapi/consolidado.yaml)

---

## Observabilidade

O Grafana em http://localhost:3000 tem 4 dashboards pré-configurados:

| Dashboard | O que mostra |
|-----------|-------------|
| **Negócio** | Lançamentos/min, saldo em tempo real, taxa de erros de negócio |
| **Plataforma** | Latência p50/p95/p99, throughput, circuit breakers |
| **Infraestrutura** | CPU, memória, conexões DB, Redis hit rate |
| **SLOs** | Disponibilidade e SLOs dos dois serviços |

Rastreamento distribuído (Tempo + OTEL) — cada request tem trace_id propagado da entrada no Traefik até o banco.

---

## HTTPS Local com Certificado Confiável

O gateway usa certificado auto-assinado por padrão. Para HTTPS sem aviso no browser:

```bash
# Instalar mkcert (uma vez por máquina)
# macOS: brew install mkcert | Linux: apt install mkcert | Windows: choco install mkcert

mkcert -install
mkcert -cert-file traefik/certs/local.pem \
       -key-file  traefik/certs/local-key.pem \
       localhost 127.0.0.1

docker compose restart traefik
```

---

## Estrutura do Repositório

```
.
├── services/
│   ├── lancamentos/            # Serviço de Lançamentos (Spring Boot 3 + Java 21)
│   │   ├── src/main/java/      # Arquitetura Hexagonal: domain / application / adapter
│   │   ├── src/test/java/      # 40 testes (unit, slice, integração)
│   │   └── build.gradle        # OpenAPI Generator + Resilience4j + Flyway
│   └── consolidado/            # Serviço de Consolidação Diária
│       ├── src/main/java/      # Mesma estrutura hexagonal
│       ├── src/test/java/      # Testes (unit, slice, integração)
│       └── build.gradle        # OpenAPI Generator + Resilience4j + Flyway + JaCoCo
├── contracts/openapi/          # Contratos OpenAPI 3.1 (source of truth)
│   ├── lancamentos.yaml
│   └── consolidado.yaml
├── tests/k6/                   # Testes de carga: smoke / load / stress / idempotencia
├── observability/              # Prometheus, Grafana dashboards, Loki, Tempo, OTEL
├── traefik/                    # API Gateway config + middlewares
├── terraform/                  # IaC para AWS (prod)
├── docs/                       # Portal MkDocs: ADRs, arquitetura, decisões
└── docker-compose.yml          # Orquestração local completa
```

---

## Desenvolvimento Local dos Serviços

```bash
# Build sem subir containers (valida compilação e testes)
cd services/lancamentos
./gradlew build

# Subir apenas a infra necessária e rodar o serviço pela IDE/CLI
docker compose up postgres-lancamentos rabbitmq redis -d
./gradlew bootRun

# Documentação local (sem Docker)
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
mkdocs serve
```

Os contratos OpenAPI em `contracts/openapi/` são compilados automaticamente pelo Gradle — qualquer mudança no `.yaml` que quebre a interface gera erro de compilação. Esse é o comportamento esperado: **contratos são lei**.
