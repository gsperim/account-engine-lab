---
tags:
  - adr
  - decisao
  - seguranca
---

# ADR-004 — Validação de Tokens: JWT com Validação Local via JWKS

**Papéis:** 🔒 Arquiteto de Segurança · ⚙️ Arquiteto de Tecnologia
**Data:** 2026-05-07
**Status:** Aceito
**Requisitos:** [NFR-05](../negocio/requisitos.md#nfr-05)

---

## Contexto

Cada serviço precisa validar o token de autenticação em toda requisição recebida. Dois modelos de token existem para isso, com consequências arquiteturais opostas:

- **Tokens opacos** — string aleatória sem informação embutida; o serviço precisa chamar o auth service a cada requisição para perguntar "esse token é válido?" (*token introspection*). A validação é sempre autoritativa, mas o auth service se torna um bottleneck de runtime — cada chamada de API gera uma chamada adicional ao auth service.

- **JWT (JSON Web Token)** — token autocontido assinado digitalmente; o serviço valida a assinatura localmente usando a chave pública do auth service, sem nenhuma chamada de rede por requisição.

Dado que [NFR-05](../negocio/requisitos.md#nfr-05) exige autenticação em 100% das requisições e o sistema tem dois serviços com APIs expostas (Lançamentos e Consolidação), a escolha do modelo de token afeta diretamente a disponibilidade e a latência.

---

## Alternativas Consideradas

| Opção | Vantagens | Razão do descarte |
|-------|-----------|-------------------|
| **Tokens opacos + introspection** | Revogação imediata; auth service tem visibilidade de todas as sessões ativas | Chamada de rede por requisição — auth service vira bottleneck; se o auth service cair, nenhuma API responde |
| **Sessões server-side** | Revogação imediata; modelo familiar | Estado centralizado incompatível com escalabilidade horizontal ([P-07](../negocio/principios.md#p-07)); não se aplica a integração máquina-a-máquina (PDV) |
| **JWT com validação online (introspection)** | JWT como formato + revogação imediata | Combina o pior dos dois mundos: overhead de parsing do JWT e chamada de rede por requisição |

---

## Decisão

Adotar **JWT com validação local via JWKS**.

O auth service assina os tokens com um par de chaves assimétricas (RS256 ou ES256). Cada serviço baixa as chaves públicas do endpoint JWKS (`/.well-known/jwks.json`) **uma vez** e mantém cache local. A cada requisição, a validação é inteiramente local:

1. Extrair o `kid` (key ID) do header do JWT
2. Buscar a chave pública correspondente no cache JWKS local
3. Verificar assinatura criptográfica
4. Verificar `exp` (expiração), `iss` (emissor), `aud` (audience) e escopos necessários

O cache JWKS é renovado apenas em dois momentos:
- O `kid` do token não é encontrado no cache (rotação de chave em andamento)
- Restart do serviço

**TTL dos tokens:** 15 minutos para access tokens; refresh tokens de longa duração (configurável) para renovação transparente ao usuário.

---

## Consequências

### Positivas

- **Zero chamada de rede por requisição** — validação local elimina o auth service como bottleneck de runtime; os serviços validam tokens de forma completamente independente
- **Auth service não é ponto único de falha em runtime** — se o auth service cair após o cache JWKS estar aquecido, os serviços continuam validando tokens existentes normalmente
- **Stateless por design** — alinhado com [P-07](../negocio/principios.md#p-07); qualquer instância valida qualquer token sem estado compartilhado
- **Suporte nativo a múltiplos fluxos** — o mesmo mecanismo de validação atende Authorization Code (Caixa/Gestor) e Client Credentials (PDV) sem distinção no serviço receptor

### Negativas / Trade-offs

- **Revogação não é imediata** — um token revogado permanece válido até expirar; mitigado pelo TTL curto de 15 minutos. Logout do usuário invalida o refresh token no auth service, mas o access token ainda funciona pelo tempo restante
- **Gestão do cache JWKS** — os serviços precisam implementar lógica de refresh do cache quando encontram um `kid` desconhecido (rotação de chaves); bibliotecas padrão (e.g., `python-jose`, `jsonwebtoken`, `nimbus-jose-jwt`) tratam isso automaticamente
- **Tamanho do token** — JWTs são maiores que tokens opacos; impacto desprezível para APIs REST mas relevante se transmitidos em headers de alta frequência

### Mitigação da revogação

Para casos que exigem revogação imediata (ex: comprometimento de credencial), a estratégia complementar é uma **lista de revogação em cache Redis** — o serviço verifica se o `jti` (JWT ID) está na lista negra antes de aceitar o token. Essa lista contém apenas tokens ainda não expirados, mantendo o conjunto pequeno. Decisão de implementar ou não fica para o ADR de segurança detalhado na Etapa 5.
