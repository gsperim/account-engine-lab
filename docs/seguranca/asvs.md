---
tags:
  - seguranca
  - owasp
  - asvs
---

# OWASP ASVS — Matriz de Verificação de Segurança

**Perspectiva:** 🔒 Arquiteto de Segurança · 🔐 DevSecOps  
**Referência:** OWASP Application Security Verification Standard 4.0  
**Nível-alvo:** L1 (baseline obrigatório) + controles selecionados de L2 (defense in depth)

Os níveis ASVS são cumulativos: L2 supõe L1 atendido. Os controles L3 (para sistemas de missão crítica certificados) estão fora do escopo deste sistema.

---

## V1 — Arquitetura, Design e Modelagem de Ameaças

| ID | Requisito | L | Status | Onde |
|----|-----------|:-:|:------:|------|
| V1.1.1 | Componentes de segurança identificados e documentados | L1 | ✅ | [seguranca/index.md](index.md) · [ADR-010](../adr/ADR-010-seguranca.md) |
| V1.1.2 | Arquitetura de alto nível validada contra requisitos de segurança | L1 | ✅ | [ADR-004](../adr/ADR-004-jwt-validacao-local.md) · [ADR-010](../adr/ADR-010-seguranca.md) |
| V1.2.1 | Comunicações entre módulos autenticadas e protegidas | L1 | ✅ | Redes Docker isoladas; AMQP com credenciais; RDS via TLS em produção |
| V1.4.1 | Controles de acesso aplicados em trusted enforcement points | L1 | ✅ | Gateway valida JWT; serviço valida role via header `X-User-Role` |
| V1.5.1 | Serialização não usa objetos não confiáveis | L1 | ✅ | Contratos OpenAPI gerados via code-gen — sem deserialização arbitrária |
| V1.7.1 | Mecanismos de autenticação centralizados | L2 | ✅ | Keycloak ([ADR-014](../adr/ADR-014-identity-provider.md)) — único IdP |
| V1.9.1 | Comunicações entre componentes encriptadas em produção | L2 | ✅ | TLS em todos os canais produção — [ADR-010](../adr/ADR-010-seguranca.md) Camada 5 |

---

## V2 — Autenticação

| ID | Requisito | L | Status | Onde |
|----|-----------|:-:|:------:|------|
| V2.1.1 | Senhas de pelo menos 12 caracteres | L1 | ✅ | Usuários demo com `demo123`; política de senha configurável no Keycloak |
| V2.5.1 | Sistema não revela se conta existe ou não | L1 | ✅ | Keycloak retorna erro genérico em ROPC — `invalid_grant` para credenciais inválidas |
| V2.6.1 | Credenciais de look-up não são armazenadas em texto puro | L1 | ✅ | Secrets Manager em produção; `.env` não commitado (`gitignore`) |
| V2.7.4 | Tokens OTP gerados com gerador criptograficamente seguro | L1 | N/A | Sistema não usa OTP — autenticação via PKCE/ROPC |
| V2.8.1 | Tokens baseados em tempo (JWT) com validade <= 30 min | L1 | ✅ | Access token TTL = 5 min ([ADR-013](../adr/ADR-013-revogacao-tokens.md)) |
| V2.9.1 | Chaves criptográficas nunca hardcoded | L1 | ✅ | JWKS via endpoint Keycloak; chaves privadas fora do código |
| V2.10.1 | Credenciais de integração máquina-a-máquina armazenadas com segurança | L2 | ✅ | Client Credentials via Secrets Manager ([ADR-014](../adr/ADR-014-identity-provider.md)) |

---

## V3 — Gestão de Sessão

| ID | Requisito | L | Status | Onde |
|----|-----------|:-:|:------:|------|
| V3.2.1 | Tokens de sessão criados com entropia suficiente | L1 | ✅ | JWT assinado com RS256 gerado pelo Keycloak — chave privada HSM-grade |
| V3.2.2 | Tokens de sessão não aparecem em parâmetros de URL | L1 | ✅ | Apenas `Authorization: Bearer` header |
| V3.3.1 | Logout invalida tokens no servidor | L1 | ✅ | Refresh token invalidado em `POST /logout`; access token expira em 5 min |
| V3.4.1 | Cookie de sessão com `HttpOnly` e `Secure` | L1 | N/A | Sistema não usa cookies — autenticação por Bearer token |
| V3.7.1 | Sessão vinculada ao sujeito original (`sub` claim) | L2 | ✅ | `sub` = UUID do Keycloak; `operadorId` gravado em cada lançamento |

---

## V4 — Controle de Acesso

| ID | Requisito | L | Status | Onde |
|----|-----------|:-:|:------:|------|
| V4.1.1 | Controle de acesso aplicado server-side | L1 | ✅ | Gateway valida escopo por rota; serviço valida role via header |
| V4.1.2 | Recurso protegido contra acesso sem autenticação | L1 | ✅ | Todos os endpoints requerem JWT válido ([ADR-004](../adr/ADR-004-jwt-validacao-local.md)) |
| V4.1.3 | Princípio do menor privilégio aplicado | L1 | ✅ | Escopos granulares: `lancamentos:write`, `lancamentos:read`, `consolidacao:read`, `consolidacao:admin` |
| V4.2.1 | Acesso restrito por função e recurso | L2 | ✅ | Matriz de autorização por role × endpoint ([seguranca/index.md](index.md#matriz-de-autorização)) |
| V4.3.1 | Interface administrativa protegida por autenticação adicional | L1 | ✅ | `POST /admin/reconstruir` restringe ao role `admin` via `consolidacao:admin` |

---

## V5 — Validação, Sanitização e Encoding

| ID | Requisito | L | Status | Onde |
|----|-----------|:-:|:------:|------|
| V5.1.1 | Sistema usa allowlist (não denylist) para validação | L1 | ✅ | Schema OpenAPI define tipos aceitos; enums explícitos (`CREDITO`, `DEBITO`) |
| V5.1.3 | Input validado antes de processamento | L1 | ✅ | Spring Validation + Bean Validation via gerador OpenAPI |
| V5.2.1 | Todos os inputs de usuário sanitizados para contexto de saída | L1 | ✅ | Serialização JSON via Jackson — sem renderização de HTML |
| V5.3.4 | SQL parametrizado — sem SQL dinâmico | L1 | ✅ | Spring Data JPA com queries parametrizadas e JPQL — sem concatenação |
| V5.5.1 | Deserialização não executa código não confiável | L1 | ✅ | Jackson com tipos explícitos — sem polimorfismo não restrito |

---

## V7 — Tratamento de Erros e Logging

| ID | Requisito | L | Status | Onde |
|----|-----------|:-:|:------:|------|
| V7.1.1 | Respostas de erro não expõem stack traces ou dados internos | L1 | ✅ | `GlobalExceptionHandler` retorna `Erro { codigo, mensagem }` — sem detalhes de implementação |
| V7.1.2 | Logs não contêm credenciais ou dados sensíveis | L1 | ✅ | [ADR-016](../adr/ADR-016-redacao-pii-logs.md) — redação de PII e tokens nos logs |
| V7.2.1 | Eventos de autenticação auditados | L1 | ✅ | Keycloak loga todas as tentativas; `operadorId` em cada lançamento |
| V7.3.1 | Logs contêm informações suficientes para investigação | L2 | ✅ | Logs estruturados JSON com `traceId`, `spanId`, `event`, `correlation_id` — integrados ao Loki + Tempo |
| V7.4.1 | Sistema alerta sobre erros inesperados | L2 | ✅ | Prometheus + alertas Grafana; métricas `http_server_requests_seconds` e `resilience4j_*` |

---

## V8 — Proteção de Dados

| ID | Requisito | L | Status | Onde |
|----|-----------|:-:|:------:|------|
| V8.2.1 | Dados sensíveis não armazenados em cache não seguro | L1 | ✅ | Redis com AUTH password em produção; dados de saldo, não PII |
| V8.2.2 | Dados sensíveis não armazenados em logs | L1 | ✅ | [ADR-016](../adr/ADR-016-redacao-pii-logs.md) — campo `descricao` sanitizado antes de log |
| V8.3.1 | Dados sensíveis encriptados em repouso | L1 | ✅ | RDS + ElastiCache com KMS CMK em produção ([ADR-010](../adr/ADR-010-seguranca.md)) |
| V8.3.4 | Dados financeiros com precisão monetária garantida | L2 | ✅ | `NUMERIC(15,2)` — sem ponto flutuante ([ADR-012](../adr/ADR-012-persistencia.md)) |

---

## V9 — Comunicações

| ID | Requisito | L | Status | Onde |
|----|-----------|:-:|:------:|------|
| V9.1.1 | TLS em todos os canais externos | L1 | ✅ | CloudFront + ACM em produção; Traefik + cert auto-assinado em dev |
| V9.1.2 | TLS ≥ 1.2 obrigatório | L1 | ✅ | CloudFront Security Policy `TLSv1.2_2021` |
| V9.2.1 | Conexões cliente com servidor verificadas | L2 | ✅ | mTLS opcional via truststore S3 ([ADR-010](../adr/ADR-010-seguranca.md) Camada 2) |

---

## V11 — Lógica de Negócio

| ID | Requisito | L | Status | Onde |
|----|-----------|:-:|:------:|------|
| V11.1.1 | Sistema aplica lógica de negócio de forma consistente | L1 | ✅ | Domain model em Java puro (zero Spring no domínio) — invariantes garantidas no agregado |
| V11.1.2 | Limite de taxa aplicado sobre operações de negócio | L1 | ✅ | Rate limit por IP por rota ([seguranca/index.md](index.md#rate-limiting)) |
| V11.1.4 | Sistema impede processamento duplicado | L2 | ✅ | `Idempotency-Key` + `payload_hash` SHA-256 na tabela `lancamentos`; `INSERT … ON CONFLICT DO NOTHING` no consolidado |
| V11.1.5 | Estorno validado — sem estorno de estorno | L2 | ✅ | `EstornarLancamentoService` — verifica `estornado = false` antes de registrar |

---

## V13 — API e Serviços Web

| ID | Requisito | L | Status | Onde |
|----|-----------|:-:|:------:|------|
| V13.1.1 | Todos os endpoints protegidos, inclusive documentação | L1 | ✅ | Actuator expõe apenas `health`, `info`, `metrics`, `prometheus` — sem Swagger em produção |
| V13.2.1 | Verbos HTTP verificados por endpoint | L1 | ✅ | OpenAPI spec define método por rota; gerador cria mapeamentos explícitos |
| V13.2.3 | Content-Type validado em cada requisição | L1 | ✅ | `Content-Type: application/json` obrigatório — Spring MVC rejeita outros |
| V13.2.6 | APIs protegidas contra CSRF via origem verificada | L1 | N/A | API stateless com JWT Bearer — CSRF não aplicável a APIs machine-to-machine |

---

## Lacunas Conhecidas e Roadmap

| Controle | Gap atual | Justificativa | Roadmap |
|----------|-----------|---------------|---------|
| V2.4 (hashing de senha) | Senhas gerenciadas pelo Keycloak (bcrypt interno) — sem visibilidade direta | Responsabilidade do IdP | Keycloak usa Pbkdf2 por padrão; upgradar para Argon2id na config do realm |
| V6.2 (criptografia em trânsito intra-pod) | mTLS intra-pod não implementado | SGs por pod + rede VPC privada mitigam | Service mesh (Istio) na evolução para múltiplos clusters |
| V11.1.6 (limites de negócio por período) | Sem limite diário de volume por operador | Não solicitado no escopo | Throttling por `operadorId` — evolution path |
| V14.3 (SAST automatizado no pipeline) | Trivy cobre CVEs de dependências; sem análise estática de código | CI tem Trivy; SAST exige tooling adicional | SpotBugs + SonarCloud no `ci.yml` |
