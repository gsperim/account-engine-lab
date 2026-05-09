---
tags:
  - seguranca
  - autenticacao
  - autorizacao
---

# Segurança

**Perspectiva:** 🔒 Arquiteto de Segurança · 🔐 DevSecOps  
**Framework:** ArchiMate — Motivation View (risk & compliance) + C4 L2  
**Requisitos:** [NFR-05](../negocio/requisitos.md#nfr-05), [NFR-09](../negocio/requisitos.md#nfr-09), [C-04](../negocio/requisitos.md#c-04)

A segurança é endereçada em duas camadas complementares:

- **Camada de sistema** (este documento) — identidade, autorização por recurso, proteção dos dados do negócio, superfície de ataque
- **Camada de infraestrutura AWS** (já documentada) — WAF, mTLS, KMS, GuardDuty, CloudTrail, Security Hub → [ADR-010](../adr/ADR-010-seguranca.md)

---

## Modelo de Identidade

### Atores e Fluxos OAuth2

O sistema suporta dois fluxos OAuth2 distintos conforme o tipo de ator:

```mermaid
flowchart LR
    subgraph Humanos
        CA["Caixa\n(Operador de PDV)"]
        GE["Gestor\n(Proprietário)"]
    end

    subgraph Sistemas
        PDV["Sistema PDV\n(máquina)"]
    end

    subgraph Auth["Auth Service (OAuth2 / OIDC)"]
        AC["Authorization Code\n+ PKCE"]
        CC["Client Credentials"]
    end

    GW["API Gateway\n(JWT Authorizer)"]

    CA -->|"login via browser/app"| AC
    GE -->|"login via browser/app"| AC
    PDV -->|"client_id + secret"| CC
    AC -->|"access_token (JWT)"| GW
    CC -->|"access_token (JWT)"| GW
```

| Fluxo | Atores | Quando usar |
|-------|--------|-------------|
| **Authorization Code + PKCE** | Caixa, Gestor | Usuários humanos via navegador ou app mobile — protege contra interceptação do código |
| **Client Credentials** | Sistema PDV | Integração máquina-a-máquina sem usuário humano — client_id + secret gerenciados pelo Secrets Manager |

### Claims e Escopos JWT

Todo access token carrega os seguintes claims relevantes para autorização:

```json
{
  "sub":    "usr_7f3b9a10",
  "iss":    "https://auth.fluxocaixa.internal",
  "aud":    "api.fluxocaixa",
  "exp":    1746800000,
  "iat":    1746799100,
  "jti":    "550e8400-e29b-41d4-a716-446655440000",
  "role":   "caixa",
  "scope":  "lancamentos:write lancamentos:read"
}
```

| Claim | Finalidade |
|-------|-----------|
| `sub` | Identificador único do sujeito — usado para auditoria ([NFR-09](../negocio/requisitos.md#nfr-09)) |
| `jti` | JWT ID único — chave de idempotência na lista de revogação |
| `role` | Papel do ator (`caixa`, `gestor`, `pdv`, `admin`) |
| `scope` | Escopos autorizados — validados pelo API Gateway e pelos serviços |
| `exp` | Expiração — TTL de **15 minutos** para access tokens |

### Escopos do Sistema

| Escopo | Descrição |
|--------|-----------|
| `lancamentos:write` | Registrar lançamentos e estornos |
| `lancamentos:read` | Consultar lançamentos por período |
| `consolidacao:read` | Consultar saldo consolidado e por período |
| `consolidacao:admin` | Reconciliação periódica e recálculo assíncrono |

---

## Matriz de Autorização

| Endpoint | Caixa | Gestor | PDV (CC) | Admin |
|----------|:-----:|:------:|:--------:|:-----:|
| `POST /lancamentos` | ✅ | ❌ | ✅ | ❌ |
| `POST /lancamentos/{id}/estorno` | ✅ | ❌ | ❌ | ❌ |
| `GET /lancamentos` | ✅ | ✅ | ❌ | ✅ |
| `GET /consolidacao/{data}` | ❌ | ✅ | ❌ | ✅ |
| `GET /consolidacao/periodo` | ❌ | ✅ | ❌ | ✅ |
| `POST /lancamentos/recalcular` | ❌ | ❌ | ❌ | ✅ |
| `POST /consolidacao/reconciliacao` | ❌ | ❌ | ❌ | ✅ |

**Regra de autorização — onde cada verificação ocorre:**

| Verificação | Responsável | Mecanismo |
|-------------|-------------|-----------|
| Assinatura JWT válida | **Gateway** | JWKS cache local — zero chamada de rede por requisição |
| Token não expirado (`exp`) | **Gateway** | Verificação local do claim |
| Escopo presente (`scope`) | **Gateway** | Comparação do claim com o escopo exigido pela rota |
| Role autorizado para a operação | **Serviço** | Leitura do header `X-User-Role` injetado pelo gateway |

O serviço **não valida o JWT** e **não consulta nenhum store de autenticação**. Recebe os claims pré-validados como headers (`X-User-Id`, `X-User-Role`, `X-Scopes`) e confia neles — o gateway é o único ponto de validação criptográfica.

---

## Autenticação Serviço a Serviço

Os serviços internos não recebem tokens JWT de usuário — eles se autenticam entre si e com a infraestrutura via credenciais dedicadas.

```mermaid
flowchart TD
    subgraph Lançamentos
        SVC_L["Serviço de\nLançamentos"]
        RELAY["Outbox Relay"]
    end

    subgraph Consolidação
        SVC_C["Serviço de\nConsolidação"]
    end

    PG_L[("PostgreSQL\nLançamentos")]
    PG_C[("PostgreSQL\nConsolidação")]
    RB[["RabbitMQ"]]
    RD[("Redis")]

    SVC_L -->|"usuário + senha\n(Secrets Manager)"| PG_L
    RELAY -->|"usuário + senha\n(Secrets Manager)"| PG_L
    RELAY -->|"AMQP + credenciais\n(Secrets Manager)"| RB
    SVC_C -->|"AMQP + credenciais\n(Secrets Manager)"| RB
    SVC_C -->|"usuário + senha\n(Secrets Manager)"| PG_C
    SVC_C -->|"AUTH password\n(Secrets Manager)"| RD
```

| Conexão | Mecanismo | Ambiente local | Produção |
|---------|-----------|---------------|---------|
| Serviço → PostgreSQL | Username + password | Variável de ambiente via `.env` | RDS Proxy + IAM Authentication |
| Relay / Consumer → RabbitMQ | AMQP credenciais | Variável de ambiente | Secrets Manager — rotação automática |
| Consolidação → Redis | AUTH password | Variável de ambiente | Secrets Manager + KMS |
| Traefik → Serviços | Sem autenticação | Rede Docker isolada | VPC interna — sem acesso externo |

**Por que sem mTLS interno (local)?** O ambiente local usa redes Docker isoladas (`lancamentos-data`, `consolidacao-data`) — os containers não são acessíveis externamente. Em produção, o tráfego intra-pod no EKS usa a CNI da VPC com SGs dedicados, mantendo o isolamento sem overhead de certificados laterais.

---

## Ciclo de Vida do Token e Revogação

Decisão completa em [ADR-013](../adr/ADR-013-revogacao-tokens.md). Resumo:

| Parâmetro | Valor |
|-----------|-------|
| Access token TTL | **5 minutos** |
| Refresh token TTL | **24 horas** (configurável) |
| Refresh rotation | A cada uso — refresh anterior invalidado |
| Blacklist Redis | **Não implementada** — ver ADR-013 |

```mermaid
sequenceDiagram
    actor U as Usuário
    participant A as Auth Service
    participant GW as API Gateway
    participant S as Serviço

    U->>A: login (Authorization Code + PKCE)
    A-->>U: access_token (TTL 5min) + refresh_token

    U->>GW: requisição + Bearer token
    GW->>GW: valida assinatura + exp + scope (JWKS local)
    GW->>S: headers X-User-Id, X-User-Role, X-Scopes
    S-->>U: resposta

    note over U,S: Logout explícito

    U->>A: POST /logout (refresh_token)
    A->>A: invalida refresh_token no store
    A-->>U: HTTP 200

    note over GW: Access token expira em até 5 minutos
    note over A: Sem refresh_token válido, não há renovação
```

**Por que não há blacklist Redis:** adicionar uma consulta Redis por requisição violaria a separação de responsabilidades (auth é função do gateway, não do serviço) e anularia a vantagem principal do JWT — validação local sem I/O. A janela de 5 minutos após logout é o trade-off aceito para este perfil de risco. Alternativas avaliadas e descartadas estão documentadas no ADR-013.

---

## Proteção de Dados

### Em Trânsito

| Caminho | Local | Produção |
|---------|-------|---------|
| Cliente → API Gateway | HTTPS via Traefik (cert auto-signed) | HTTPS via CloudFront + ACM |
| API Gateway → Serviços | HTTP (rede interna Docker) | HTTPS via ALB interno (certificado privado) |
| Serviços → PostgreSQL | Plain TCP (rede isolada Docker) | TLS obrigatório via RDS + RDS Proxy |
| Relay/Consumer → RabbitMQ | AMQP plain (rede isolada) | AMQPS (TLS) — porta 5671 |
| Serviços → Redis | Plain TCP (rede isolada) | TLS via ElastiCache + KMS |
| CloudFront → API Gateway | HTTPS + header `X-Origin-Verify` | — |

### Em Repouso

| Dado | Local | Produção |
|------|-------|---------|
| PostgreSQL | Sem encryption (dev only) | RDS encryption + KMS CMK |
| Redis | Sem encryption (dev only) | ElastiCache encryption + KMS CMK |
| Secrets (senhas, tokens) | Arquivo `.env` (não commitado) | AWS Secrets Manager + KMS CMK |
| Logs de auditoria | stdout local | CloudWatch Logs + KMS CMK |
| Backups | Não aplicável | AWS Backup cross-region + WORM lock 3 dias |

---

## Superfície de Ataque e Controles

### Exposição Externa

| Ponto de exposição | Ameaça | Controle |
|-------------------|--------|---------|
| `POST /lancamentos` | Requisições não autenticadas, flood | JWT validation ([ADR-004](../adr/ADR-004-jwt-validacao-local.md)) + rate limiting ([NFR-07](../negocio/requisitos.md#nfr-07)) + WAF (prod) |
| `GET /consolidacao/*` | Enumeração de saldos por data | JWT + escopo `consolidacao:read` — apenas Gestor/Admin |
| `POST /lancamentos/{id}/estorno` | Estorno fraudulento | JWT + escopo `lancamentos:write` + validação de negócio ([RF-08](../negocio/requisitos.md#rf-08): duplo estorno bloqueado) |
| Endpoint JWKS (`/.well-known/jwks.json`) | Substituição de chave pública | Auth service controla o endpoint — serviços fazem cache, não aceitam chaves de terceiros |

### Superfície Interna

| Componente | Ameaça | Controle |
|-----------|--------|---------|
| PostgreSQL | Acesso direto de pods não autorizados | SG dedicado — aceita conexão apenas do RDS Proxy; localmente, rede Docker isolada sem porta exposta ao host |
| RabbitMQ | Consumer malicioso publicando eventos falsos | Credenciais por serviço — Relay tem permissão de publish; Consumer tem permissão de consume only |
| Redis | Leitura/escrita de cache por serviço não autorizado | Auth password obrigatório; rede isolada sem acesso externo |
| Outbox Relay | Comprometimento → publicação de eventos falsos | Blast radius limitado: Relay só publica eventos gerados pela própria tabela `outbox` do Lançamentos |
| `descricao` (campo livre) | Inserção de PII não estruturado | Validação no frontend + rejeição de padrões CPF/CNPJ no backend ([RF-05](../negocio/requisitos.md#rf-05)) |

### Injeção e Inputs Maliciosos

| Vetor | Controle |
|-------|---------|
| SQL Injection | Queries parametrizadas via ORM/prepared statements — sem concatenação de strings SQL |
| Payload oversized | Content-Length limit no API Gateway (ex: 64 KB para lançamentos) |
| Formato inválido | Validação de schema na camada de aplicação antes de qualquer persistência |
| Replay de evento | Idempotência via PK em `lancamentos_processados` — evento duplicado é absorvido sem efeito |
