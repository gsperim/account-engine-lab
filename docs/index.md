# Controle de Fluxo de Caixa Diário

Sistema para registro de débitos e créditos e consulta de saldo consolidado por dia — com dois serviços independentes e garantia de que o registro de lançamentos nunca para, mesmo com falha no módulo de saldo.

---

## Por onde começar

| Se você quer entender... | Vá para... |
|--------------------------|-----------|
| O problema de negócio e as decisões que guiaram o projeto | [Visão Executiva](visao-executiva.md) |
| A arquitetura técnica — NFRs, ADRs e evidências no código | [Síntese da Arquitetura](arquitetura/index.md) |
| As decisões arquiteturais individuais (18 ADRs) | [Decisões Arquiteturais](adr/index.md) |
| Como rodar o sistema localmente | [Como executar](#como-executar-localmente) |
| O que ficou de fora e por quê | [Evoluções Futuras](evolucoes.md) |

---

## O sistema em uma linha

Dois microserviços Spring Boot 3.5 + Java 21, comunicação assíncrona via RabbitMQ com Outbox Pattern, cache Redis no caminho de leitura, observabilidade com PLT (Prometheus + Loki + Tempo) + OTEL.

```
Caixa/PDV → API Gateway → Lançamentos → Outbox → RabbitMQ → Consolidado
                                                                    ↓
Gestor → API Gateway → Consolidado → Redis cache (TTL 1h) → PostgreSQL
```

**NFR crítico:** os dois serviços são fisicamente independentes — falha no Consolidado não interrompe o registro de lançamentos. Verificado em chaos engineering (5 experimentos documentados).

---

## Como executar localmente

```bash
git clone https://github.com/gsperim/account-engine-lab
cd account-engine-lab
docker compose up --profile app
```

O ambiente completo sobe com **24 containers**: 2 serviços de negócio + outbox relay + 2 bancos PostgreSQL + Redis + RabbitMQ + Keycloak + Traefik + stack de observabilidade (PLG + Tempo + OTEL).

| Serviço | URL local |
|---------|-----------|
| API Lançamentos | `https://lancamentos.localhost` |
| API Consolidado | `https://consolidado.localhost` |
| Grafana | `http://localhost:3000` |
| Keycloak | `http://localhost:8180` |
| RabbitMQ Management | `http://localhost:15672` |
| Diagramas C4 (Structurizr Lite) | `http://localhost:8080` |
| Swagger UI (contratos OpenAPI) | `http://localhost:8070` |
| Documentação (MkDocs) | `http://localhost:8000` |

---

## Artefatos publicados

| Artefato | Link |
|----------|------|
| Documentação completa | [gsperim.github.io/account-engine-lab](https://gsperim.github.io/account-engine-lab/) |
| Diagramas C4 (Structurizr) | [.../c4/](https://gsperim.github.io/account-engine-lab/c4/) |
| Relatório de testes — Lançamentos | [.../tests/lancamentos/](https://gsperim.github.io/account-engine-lab/tests/lancamentos/) |
| Relatório de testes — Consolidado | [.../tests/consolidado/](https://gsperim.github.io/account-engine-lab/tests/consolidado/) |
| Cobertura JaCoCo — Lançamentos | [.../coverage/lancamentos/](https://gsperim.github.io/account-engine-lab/coverage/lancamentos/) |
| Cobertura JaCoCo — Consolidado | [.../coverage/consolidado/](https://gsperim.github.io/account-engine-lab/coverage/consolidado/) |
| Estimativa de custo AWS (Infracost) | [.../infracost/](https://gsperim.github.io/account-engine-lab/infracost/) |

---

## Estrutura da documentação

```
Início          → visão executiva das 9 fases do projeto
Negócio         → drivers, stakeholders, princípios, requisitos
Arquitetura     → síntese de decisões, C4, dados, ADRs (18)
Segurança       → autenticação, RBAC, OWASP ASVS, continuidade, conformidade
Observabilidade → stack PLT, SLOs, dashboards, alertas
Implementação   → visão técnica, chaos engineering
Infraestrutura  → docker-compose, IaC Terraform (AWS), pipeline CI/CD
```
