---
tags:
  - adr
  - infraestrutura
  - segurança
---

# ADR-007 — API Gateway: Traefik sobre nginx

**Data:** 2026-05-08
**Status:** Aceito
**Papéis:** 🏗️ Arquiteto de Infraestrutura · 🔒 Arquiteto de Segurança · ⚙️ Arquiteto de Tecnologia
**Requisitos relacionados:** [NFR-07](../negocio/requisitos.md#nfr-07) (rate limiting) · [ADR-004](ADR-004-jwt-validacao-local.md) (JWT local)

---

## Contexto

O sistema precisa de um API Gateway que centralize:

1. **Roteamento** — encaminhar `/lancamentos/*` e `/consolidacao/*` para os serviços corretos
2. **Rate limiting** — proteção contra abuso por origem ([NFR-07](../negocio/requisitos.md#nfr-07))
3. **TLS** — terminação HTTPS antes de chegar aos serviços
4. **JWT** — validação local via JWKS sem chamada ao serviço de autenticação por requisição ([ADR-004](ADR-004-jwt-validacao-local.md))

O setup inicial usava nginx com um arquivo `gateway.conf` de configuração estática. A decisão não foi documentada e a escolha foi revisada.

---

## Decisão

**Traefik v3.0**, com configuração via labels no `docker-compose.yml` e arquivos YAML dinâmicos em `traefik/dynamic/`.

---

## Justificativa

### Por que Traefik é mais adequado para este projeto

| Necessidade | nginx | Traefik v3 |
|-------------|-------|-----------|
| Roteamento declarado junto ao serviço | Arquivo `gateway.conf` separado — desacoplado do serviço | Labels no docker-compose — rota e serviço coexistem |
| TLS local sem configuração manual | Requer módulo `ssl` + cert gerado à parte | Auto-signed cert automático; ACME nativo para produção |
| Rate limiting | Módulo nativo, mas configuração manual por zona | Middleware `rateLimit` nativo, aplicado via label |
| JWT validação local (ADR-004) | Exige OpenResty (nginx + Lua) ou módulo externo | Plugin `jwt` valida localmente via JWKS cacheado |
| Escalabilidade com docker-compose | DNS round-robin manual com `resolver 127.0.0.11` | Load balancing automático via Docker discovery |
| Headers de segurança | Configuração manual no `server {}` block | Middleware `headers` declarativo |

### Sobre o acesso ao Docker socket

O Traefik precisa de acesso read-only ao Docker socket (`/var/run/docker.sock:ro`) para descobrir serviços automaticamente. Isso é um vetor de risco — acesso read-only ao socket ainda expõe metadados dos containers.

**Mitigação adotada:** socket montado em modo `:ro` (somente-leitura). Em produção, o equivalente é o Traefik com um provider Kubernetes (via RBAC restrito) sem necessidade de socket.

---

## TLS em Trânsito

### Entrada (cliente → Traefik)

| Ambiente | Certificado | Protocolo |
|----------|------------|-----------|
| Local (desenvolvimento) | Auto-signed gerado pelo Traefik | TLS 1.2+ |
| Local com browser (opcional) | `mkcert localhost` montado em `traefik/certs/` | TLS 1.2+ |
| Produção | ACME (Let's Encrypt) via `certresolver` no label do router | TLS 1.2+ |

**Opções TLS mínimas:** TLS 1.2, cipher suites modernos (AES-256-GCM, ChaCha20-Poly1305). Configurado em `traefik/dynamic/tls.yml`.

### Interna (Traefik → serviços)

A comunicação interna entre Traefik e os serviços de aplicação usa **HTTP simples** nas redes Docker `internal: true`. Essa é uma decisão explícita, não uma omissão:

- Redes Docker com `internal: true` não têm rota externa — o tráfego nunca deixa o host
- mTLS interno adicionaria gestão de certificados por serviço sem proporcional ganho de segurança neste escopo
- Em produção com service mesh (Istio, Linkerd), mTLS é adicionado de forma transparente sem mudar o código dos serviços

### Em repouso

Criptografia de dados em repouso (volumes PostgreSQL, Redis, RabbitMQ) é responsabilidade da camada de infraestrutura:

- **Local:** sem criptografia de volume (desenvolvimento)
- **Produção:** volumes com criptografia habilitada no provedor de nuvem (EBS encrypted, GCS CMEK, etc.)
- **Kubernetes:** `StorageClass` com `encrypted: true`

Criptografia a nível de aplicação (campos individuais) não está no escopo deste projeto. A decisão detalhada é coberta na Etapa 5 (Segurança).

---

## Validação JWT (Etapa 7)

O [ADR-004](ADR-004-jwt-validacao-local.md) define validação local via JWKS — sem chamada ao serviço de autenticação por requisição. No Traefik, isso é implementado via plugin, não via `forwardAuth` (que faria chamada externa por request).

**Plugin a avaliar na Etapa 7:** `traefik-jwt-plugin` (community) ou equivalente que:
- Busca JWKS do endpoint do serviço de autenticação na inicialização
- Valida o JWT localmente usando as chaves públicas em cache
- Rejeita com `401` se inválido; passa o `X-User-ID` ao upstream se válido

---

## Alternativas consideradas

| Alternativa | Descartada porque |
|-------------|------------------|
| **nginx** | JWT local requer OpenResty (nginx + Lua módule); configuração de rotas desacoplada dos serviços; TLS e escalabilidade requerem mais configuração manual |
| **Kong** | Poderoso, mas pesado para docker-compose local; overhead de configuração desproporcional para 2 serviços |
| **Envoy** | Excelente como sidecar proxy, mas sua configuração é complexa e voltada para service meshes — overkill aqui |
| **Caddy** | Boa opção para TLS automático, mas ecossistema de plugins menor; menos middleware para rate limiting e JWT |

---

## Consequências

- `nginx/gateway.conf` e o diretório `nginx/` são removidos — substituídos por `traefik/`
- Rotas são declaradas como labels nos serviços `lancamentos` e `consolidado` no `docker-compose.yml`
- Middlewares (rate limiting, headers) são definidos em `traefik/dynamic/middlewares.yml`
- HTTPS disponível localmente sem configuração extra (cert auto-signed; `curl -k` ou browser com aviso)
- Em produção: adicionar `certresolver=letsencrypt` no label do router e configurar o resolver no `traefik.yml`
