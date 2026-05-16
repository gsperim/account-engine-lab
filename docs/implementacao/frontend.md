---
tags:
  - frontend
  - angular
  - keycloak
  - ux
---

# Frontend Angular — Plano de Implementação

**Perspectiva:** 🧩 Arquiteto de Soluções · 💻 Desenvolvedor  
**Status:** Planejado — implementação após Etapa 8  
**Etapa:** 9 (diferencial)

---

## Contexto e Justificativa

O frontend fecha o loop em três aspectos que o desafio cobre apenas como API:

1. **Segurança end-to-end real** — testes k6 usam ROPC (máquina para máquina). O frontend implementa Authorization Code + PKCE, o fluxo correto para usuário humano no browser, validando o modelo Keycloak em condições reais de uso.
2. **Diferenciação de papel na UX** — CAIXA, GESTOR e ADMIN têm visões distintas, validando na prática a matriz de autorização do SecurityConfig e do ADR-010.
3. **Sistema usável** — demonstra que a arquitetura sustenta produto final, não só endpoints.

---

## Stack

| Camada | Escolha | Motivo |
|--------|---------|--------|
| Framework | Angular 17+ (standalone components) | Sem NgModules — menos boilerplate; padrão atual do ecossistema |
| Auth | `angular-auth-oidc-client` | Suporte nativo a OIDC + Authorization Code + PKCE + refresh token rotation |
| UI | Angular Material | Consistência, acessibilidade, sem custo de design |
| HTTP | `HttpClient` + `AuthInterceptor` | Injeta `Authorization: Bearer {token}` automaticamente em todas as chamadas |
| Hospedagem | Nginx servindo `dist/` via Docker | Adiciona ao `docker-compose.yml`, atrás do Traefik |

---

## Fluxo de Autenticação

```
Browser → Traefik → Angular (Nginx :4200)
   └── /login
       → redirect para Keycloak (:8180/realms/fluxocaixa)
       → Keycloak autentica (usuário + senha)
       → redirect de volta com ?code=...
       → Angular troca code por access_token + refresh_token (PKCE)
       → HttpClient interceptor injeta:
           Authorization: Bearer {access_token}
       → Chamadas para Traefik → lancamentos:8080 ou consolidado:8081
```

**Por que não ROPC:** Resource Owner Password Credentials está depreciado no OAuth2 Security BCP. ROPC exige que a aplicação receba a senha do usuário — o fluxo correto para browser é Authorization Code + PKCE, onde a senha nunca passa pela aplicação.

---

## Estrutura de Rotas

```
/                     → redirect para /dashboard ou /login
/login                → componente de entrada (redireciona ao Keycloak)
/dashboard
  /caixa              → guard: ROLE_CAIXA
    /registrar        → formulário de lançamento
    /lancamentos      → listagem do dia + estorno
  /consolidado        → guard: ROLE_GESTOR ou ROLE_ADMIN
    /diario           → saldo do dia
    /periodo          → relatório por período
  /admin              → guard: ROLE_ADMIN
    /reconstruir      → formulário de reconstrução
/unauthorized         → página 403
```

---

## Telas por Papel

### CAIXA

#### Registrar Lançamento
- Formulário: tipo (DÉBITO / CRÉDITO), valor (BRL), data de competência, descrição
- `Idempotency-Key` gerado automaticamente (UUID v4) no frontend — exibido como referência após confirmação
- `POST /registros` com header `Idempotency-Key`
- Feedback visual: spinner → card de confirmação com ID do lançamento

#### Listar Lançamentos do Dia
- `GET /registros?data=hoje`
- Tabela: ID, tipo, valor, descrição, horário
- Botão "Estornar" por linha → confirmação modal → `POST /registros/{id}/estorno`
- Indicador visual para lançamentos já estornados (desabilitado)

---

### GESTOR

#### Saldo Diário
- Date picker → `GET /consolidacao/saldo/{data}`
- Card com: total créditos (verde), total débitos (vermelho), saldo líquido (azul), total lançamentos
- Indicador de atualização: "Atualizado há X minutos" (timestamp `ultima_atualizacao`)

#### Relatório por Período
- Date range picker (máx 90 dias) → `GET /consolidacao/saldo?data_inicio=...&data_fim=...`
- Tabela + gráfico de linha (créditos vs débitos por dia)
- Export CSV (client-side)

---

### ADMIN

*Todas as telas do GESTOR, mais:*

#### Reconstruir Consolidado
- Formulário: data início + data fim (máx 90 dias)
- Aviso: "Esta operação sobrescreve os saldos calculados com os dados do serviço de lançamentos"
- Confirmação em dois passos (botão + campo de confirmação digitado)
- `POST /admin/reconstruir`
- Resultado: card com `datas_processadas` e `divergencias_corrigidas`

---

## Configuração Keycloak

Reutiliza o realm `fluxocaixa` já configurado. Adicionar client:

```json
{
  "clientId": "fluxo-caixa-frontend",
  "publicClient": true,
  "redirectUris": ["http://localhost:4200/*"],
  "webOrigins": ["http://localhost:4200"],
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": false
}
```

`directAccessGrantsEnabled: false` — desabilita ROPC no client do frontend (segurança).

---

## Dockerfile

```dockerfile
# Stage 1 — Build
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

# Stage 2 — Runtime
FROM nginx:1.27-alpine AS runtime
COPY --from=builder /app/dist/fluxo-caixa/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

---

## Integração docker-compose

```yaml
frontend:
  build:
    context: frontend/
  labels:
    - "traefik.enable=true"
    - "traefik.http.routers.frontend.rule=Host(`localhost`) && PathPrefix(`/`)"
    - "traefik.http.routers.frontend.priority=1"
  depends_on:
    - lancamentos
    - consolidado
```

---

## Estimativa de Esforço

| Etapa | Esforço estimado |
|-------|-----------------|
| Scaffolding Angular + Keycloak PKCE + interceptor | ~4h |
| Guards de rota por role + layout por papel | ~2h |
| Telas CAIXA (registrar + listar + estornar) | ~4h |
| Telas GESTOR (saldo diário + período + gráfico) | ~3h |
| Telas ADMIN (reconstruir) | ~2h |
| Dockerfile + nginx.conf + docker-compose | ~2h |
| **Total** | **~17h** |

---

## Pendências antes de implementar

- [ ] Etapa 8 (CI/CD + Chaos Engineering) concluída
- [ ] Client `fluxo-caixa-frontend` adicionado ao realm import (`keycloak/realm-fluxocaixa.json`)
- [ ] Traefik configurado para servir `/` a partir do Nginx do frontend
