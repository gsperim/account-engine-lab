/**
 * Módulo de autenticação k6 — obtém e renova tokens JWT do Keycloak.
 *
 * Usa Resource Owner Password Credentials (ROPC) com o client `frontend-app`.
 * O estado é module-level: em k6, cada VU tem sua própria cópia do módulo,
 * então o cache é por-VU — tokens não são compartilhados entre VUs.
 *
 * Renova automaticamente 30 segundos antes do vencimento (TTL = 5 min no realm).
 * Isso garante que load tests de até 7–8 min funcionem sem 401 mid-test.
 */
import http from 'k6/http';

// Porta direta do Keycloak (HTTP) — evita dependência do Traefik para aquisição de token.
// Em CI ou ambientes sem acesso à porta 8180, sobrescrever com -e AUTH_URL=...
const AUTH_URL = __ENV.AUTH_URL ||
  'http://localhost:8180/realms/fluxocaixa/protocol/openid-connect/token';

// Cache per-VU: { [username]: { token: string, expiry: number (unix seconds) } }
const _cache = {};

/**
 * Retorna Bearer token válido para o usuário.
 * Faz login (ou re-login) automaticamente quando necessário.
 *
 * @param {string} username  ex: 'caixa.demo'
 * @param {string} password  ex: 'demo123'
 * @param {string} scope     escopos OAuth2 separados por espaço
 */
export function getToken(username, password, scope = 'openid lancamentos:write lancamentos:read consolidacao:read') {
  const now   = Math.floor(Date.now() / 1000);
  const entry = _cache[username];

  if (!entry || now > entry.expiry) {
    // String body explícita — garante form-encoding correto em todas as versões do k6
    const body = `client_id=frontend-app`
      + `&username=${encodeURIComponent(username)}`
      + `&password=${encodeURIComponent(password)}`
      + `&grant_type=password`
      + `&scope=${encodeURIComponent(scope)}`;

    // Retry com backoff — cobre Keycloak ainda no boot (status 0, 5xx, ou
    // "invalid_grant" transitório enquanto o realm está sendo importado).
    // Desiste após 3 tentativas em 4xx para não mascarar erros de configuração.
    const maxTentativas = 5;
    let res;
    for (let i = 0; i < maxTentativas; i++) {
      res = http.post(
        AUTH_URL,
        body,
        { headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          insecureSkipTLSVerify: true,
          timeout: '10s' },
      );
      if (res.status === 200) break;
      const permanente = res.status !== 0 && res.status < 500 && i >= 2;
      if (permanente) {
        console.error(`[auth] ${res.status} para ${username}: ${res.body}`);
        return null;
      }
      console.warn(`[auth] tentativa ${i + 1}/${maxTentativas} — status ${res.status}, aguardando 3s...`);
      const t = Date.now() + 3000;
      while (Date.now() < t) { /* busy-wait */ }
    }

    if (res.status !== 200) {
      console.error(`[auth] falha definitiva para ${username}: ${res.status} ${res.body}`);
      return null;
    }

    const tokenData = JSON.parse(res.body);
    _cache[username] = {
      token:  tokenData.access_token,
      expiry: now + (tokenData.expires_in || 300) - 30,
    };
  }

  return _cache[username].token;
}

// Atalhos para os usuários de teste do realm
export const CAIXA_USER  = { username: 'caixa.demo',  password: 'demo123' };
export const GESTOR_USER = { username: 'gestor.demo', password: 'demo123' };
export const ADMIN_USER  = { username: 'admin.demo',  password: 'demo123' };

export function tokenCaixa()  { return getToken(CAIXA_USER.username,  CAIXA_USER.password); }
export function tokenGestor() { return getToken(GESTOR_USER.username, GESTOR_USER.password); }
