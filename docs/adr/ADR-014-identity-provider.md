---
tags:
  - adr
  - decisao
  - seguranca
  - identidade
---

# ADR-014 — Identity Provider: Keycloak

**Papéis:** 🔒 Arquiteto de Segurança · 🧩 Arquiteto de Soluções · ⚙️ Arquiteto de Tecnologia  
**Data:** 2026-05-09  
**Status:** Aceito  
**Requisitos:** [NFR-05](../negocio/requisitos.md#nfr-05) · [ADR-004](ADR-004-jwt-validacao-local.md) · [ADR-013](ADR-013-revogacao-tokens.md)

---

## Contexto

O [ADR-004](ADR-004-jwt-validacao-local.md) definiu que a validação de JWT ocorre localmente via JWKS, e o [ADR-013](ADR-013-revogacao-tokens.md) estabeleceu a estratégia de refresh token rotation. Ambas as decisões pressupõem um servidor OAuth2/OIDC que:

1. Emita JWTs assinados com RS256 ou ES256
2. Exponha endpoint JWKS (`/realms/{realm}/protocol/openid-connect/certs`)
3. Suporte **Authorization Code + PKCE** (Caixa/Gestor — usuários humanos)
4. Suporte **Client Credentials** (Sistema PDV — integração máquina-a-máquina)
5. Implemente **refresh token rotation** com invalidação de refresh anteriores
6. Execute localmente via `docker-compose` sem dependência de serviço externo ([C-01](../negocio/requisitos.md#c-01))

O servidor de identidade nunca foi nomeado nos documentos anteriores — era referenciado apenas como "auth service". Esta decisão o nomeia e justifica a escolha.

---

## Alternativas Consideradas

### AWS Cognito

Serviço gerenciado da AWS. Suporte a OAuth2/OIDC, User Pools, integração nativa com API Gateway e IAM.

**Por que foi descartado:**

- **Não executa localmente** — Cognito é um serviço AWS puro; não há emulador oficial completo para docker-compose. Testar autenticação localmente exige mocks que divergem do comportamento real, violando o princípio de paridade local/produção.
- **Customização limitada de claims** — adicionar claims customizados ao JWT exige Lambda triggers, acoplando a lógica de identidade a função serverless específica da AWS.
- **Vendor lock-in** — o JWKS URL e os formatos de token são específicos do Cognito, tornando a migração posterior trabalhosa.

**Quando usar Cognito:** se o sistema for exclusivamente AWS e não precisar de paridade local completa. É uma evolução válida pós-lançamento — o código da aplicação não muda, apenas o JWKS URL nas variáveis de ambiente.

### Auth0

Serviço gerenciado de identidade como serviço. Excelente DX, suporte completo a OAuth2/OIDC.

**Por que foi descartado:**

- **Custo** — plano gratuito tem limite de 7.500 MAU (Monthly Active Users); além disso, custo por usuário.
- **Dependência externa** — toda autenticação depende de disponibilidade do Auth0; falha no serviço = sistema inacessível.
- **Sem execução local** — mesmo problema do Cognito para desenvolvimento e CI.

### Implementação própria (custom JWT server)

Construir um servidor OAuth2/OIDC do zero.

**Por que foi descartado:**

- **DDD Strategic Design** — Identidade e Acesso é classificado como **Supporting Domain** no [mapa de domínios](../negocio/dominios.md#2-classificação-estratégica-ddd-strategic-design): *"Comprar ou adaptar — suporta o core, mas não é diferenciador"*. A diretriz do DDD estratégico é explícita: esforço de engenharia máximo vai para o Core Domain (Gestão de Fluxo de Caixa). Construir um servidor OAuth2 próprio investe tempo de design e implementação em um domínio que não diferencia o produto — e que já tem soluções maduras e auditadas disponíveis.

- **Risco de segurança** — implementação própria de criptografia assimétrica, PKCE, refresh token rotation e JWKS exige conhecimento especializado. Erros nessa camada têm consequências graves e difíceis de detectar. Soluções consolidadas como o Keycloak têm décadas de uso em produção e auditorias de segurança independentes.

- **Custo de manutenção** — um servidor OAuth2 próprio exige atualizações de segurança, gestão de chaves, testes de conformidade com a RFC. Esse custo recorrente é incompatível com a prioridade de entregar o Core Domain.

---

## Decisão

Adotar **Keycloak 26** como servidor de identidade OAuth2/OIDC.

| Requisito | Como o Keycloak atende |
|-----------|----------------------|
| Authorization Code + PKCE | Suportado nativamente — configurado por cliente no realm |
| Client Credentials | Suportado — clientes de serviço com secret no Secrets Manager |
| JWKS endpoint | `{base_url}/realms/{realm}/protocol/openid-connect/certs` |
| RS256 / ES256 | Configurável por realm — RS256 como padrão |
| Refresh token rotation | Configurável por cliente: `Refresh Token Max Reuse: 0` |
| Execução local | `docker-compose up keycloak` — imagem oficial `quay.io/keycloak/keycloak:26.6.1` |
| Claims customizados | Protocol mappers — sem código externo |
| Alta disponibilidade (prod) | Modo cluster com Infinispan; ou substituído por Cognito sem mudar o código da aplicação |

### Configuração mínima do realm

O realm `fluxocaixa` define:

```
Clients:
  - frontend-app         → Authorization Code + PKCE · redirect: http://localhost:3000/*
  - pdv-service          → Client Credentials · service account habilitado
  - api-gateway          → bearer-only (valida tokens, não emite)

Roles:
  - caixa    → scope: lancamentos:write, lancamentos:read
  - gestor   → scope: consolidacao:read, lancamentos:read  
  - admin    → scope: consolidacao:admin, lancamentos:read
  - pdv      → scope: lancamentos:write

Token settings (access token):
  - Lifespan: 5 minutos (alinhado com ADR-013)
  - Signing: RS256
  - Claims: sub, jti, iss, aud, exp, iat, role (custom mapper), scope
```

A configuração completa do realm é exportada em `keycloak/realm-fluxocaixa.json` e importada automaticamente na inicialização do container (Etapa 7).

### Estratégia de produção

Em produção, duas opções são válidas — sem alteração no código da aplicação:

| Opção | Trade-off |
|-------|-----------|
| **Keycloak no EKS** | Controle total, sem vendor lock-in, custo de operação |
| **AWS Cognito** | Zero operação, custo por MAU, sem execução local |

A troca é possível porque a aplicação depende apenas do **JWKS URL** (variável de ambiente) — não de SDK ou API específica do provedor.

---

## Consequências

### Positivas

- **Paridade local/produção** — `docker-compose up keycloak` sobe o mesmo servidor que rodará em produção (ou equivalente funcional)
- **Zero dependência externa para desenvolvimento** — o sistema funciona completamente offline
- **Todos os fluxos OAuth2 necessários suportados** sem configuração adicional
- **Portável** — troca de Keycloak por Cognito ou outro OIDC provider é uma mudança de variável de ambiente, não de código

### Negativas / Trade-offs

- **Mais um container operacional** — em produção, Keycloak precisa de banco de dados próprio (PostgreSQL), alta disponibilidade e monitoramento. Mitigado pela opção de substituição por Cognito.
- **Cold start em desenvolvimento** — Keycloak demora ~30s para inicializar em modo dev. Mitigado por `depends_on: condition: service_healthy` no compose.
- **Configuração de realm** — a configuração inicial do realm (clients, roles, mappers) precisa ser versionada e importada. Endereçado na Etapa 7 com `realm-fluxocaixa.json`.
