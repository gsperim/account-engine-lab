---
tags:
  - adr
  - infraestrutura
  - containers
---

# ADR-006 — Runtime de Containers: docker-compose local, Kubernetes para produção

**Data:** 2026-05-07
**Status:** Aceito
**Papéis:** 🏗️ Arquiteto de Infraestrutura · ⚙️ Arquiteto de Tecnologia · 🔐 DevSecOps
**Requisitos relacionados:** [C-01](../negocio/requisitos.md#c-01) (execução local via docker-compose)

---

## Contexto

O desafio exige que o sistema seja executado localmente com um único comando. Ao mesmo tempo, um sistema financeiro de produção precisa de um runtime que suporte escalabilidade horizontal, rolling deployments, self-healing e gestão de segredos.

Essas duas necessidades — simplicidade local e robustez em produção — apontam para runtimes diferentes. A decisão precisa ser tomada explicitamente para que a escolha de ferramentas e a estrutura dos arquivos de configuração sejam consistentes ao longo do projeto.

---

## Decisão

**Local e CI:** `docker-compose` com a topologia definida em `docker-compose.yml`

**Produção:** `Kubernetes` (preferencialmente gerenciado — EKS, GKE ou AKS, dependendo da nuvem do cliente)

Os mesmos artefatos Docker (imagens) são usados nos dois ambientes. A diferença é apenas o runtime e o conjunto de manifestos de orquestração.

---

## Justificativa

### Por que docker-compose para local e CI?

| Critério | docker-compose |
|----------|---------------|
| **Requisito explícito** | C-01 exige execução local via docker-compose |
| **Curva de aprendizado** | Um único arquivo YAML define todo o ambiente — sem dependência de cluster |
| **Reprodutibilidade de CI** | GitHub Actions, GitLab CI e Jenkins suportam `docker-compose` nativamente |
| **Feedback rápido** | Sobe o ambiente completo em < 60s em hardware típico |

### Por que Kubernetes para produção?

| Necessidade | Mecanismo Kubernetes |
|-------------|---------------------|
| Escalabilidade horizontal do Consolidado (NFR-02) | `HorizontalPodAutoscaler` por CPU/RPS |
| Self-healing após falha de container | `restartPolicy: Always` + liveness probe |
| Rolling deployments sem downtime | `RollingUpdate` strategy |
| Gestão de segredos em produção | `Secret` + integração com vault (AWS Secrets Manager, HashiCorp Vault) |
| Isolamento de rede granular | `NetworkPolicy` — equivalente aos `internal: true` do docker-compose |
| Observabilidade integrada | Sidecar patterns para OpenTelemetry, Prometheus scraping via annotations |

### Por que não Kubernetes direto no local?

Minikube, kind e k3d reduzem a barreira, mas ainda exigem:
- Configuração de kubeconfig por desenvolvedor
- Pull de imagens para o registry interno do cluster
- Tempo de startup mais longo

Para desenvolvimento e CI, o custo supera o benefício. O docker-compose cobre os requisitos locais com zero atrito.

---

## Mapeamento docker-compose → Kubernetes

| Conceito docker-compose | Equivalente Kubernetes |
|------------------------|----------------------|
| `service` | `Deployment` + `Service` |
| `networks.internal: true` | `NetworkPolicy` |
| `volumes` | `PersistentVolumeClaim` |
| `--scale consolidado=3` | `replicas: 3` no Deployment + HPA |
| `.env` / `environment:` | `ConfigMap` + `Secret` |
| `healthcheck` | `livenessProbe` + `readinessProbe` |
| `depends_on` | `initContainers` |
| `restart: unless-stopped` | `restartPolicy: Always` |

---

## Trade-offs considerados

| Alternativa | Descartada porque |
|-------------|------------------|
| **Kubernetes local (kind/minikube)** | Overhead de configuração elimina o ganho de reprodutibilidade; não atende C-01 de forma simples |
| **Docker Swarm** | Menos adotado que Kubernetes; ecossistema menor; não é a direção do mercado em produção |
| **ECS (AWS) direto** | Lock-in em AWS; task definitions são mais verbosas que manifestos Kubernetes para pouco ganho adicional |
| **Nomad (HashiCorp)** | Excelente para workloads heterogêneos, mas complexidade adicional sem justificativa neste volume |

---

## Consequências

- O `docker-compose.yml` é o artefato primário de Etapa 3 e permanece como fonte de verdade para CI
- Os manifestos Kubernetes são produzidos na Etapa 8 (Pipeline e Entrega) usando o mesmo conjunto de imagens
- As variáveis de ambiente declaradas no `.env.example` têm correspondência direta nos `ConfigMap` e `Secret` do Kubernetes
- Os healthchecks definidos no docker-compose são reaproveitados como `livenessProbe` e `readinessProbe`
