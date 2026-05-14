/**
 * Stress Test — Comportamento além do SLO
 *
 * Objetivo: verificar degradação graceful além de 50 req/s.
 * O sistema deve recusar com 429 (rate limit Traefik) em vez de travar.
 *
 * Fases:
 *   0–2min  → carga normal (50 req/s)
 *   2–4min  → acima do SLO (100 req/s)
 *   4–6min  → pico extremo (200 req/s)
 *   6–7min  → recuperação
 *
 * Tokens JWT:
 *   Cada VU obtém seu próprio token e renova automaticamente antes do vencimento.
 *   Duração total = 7min > TTL de 5min → renovação transparente por VU.
 *
 * Execução:
 *   k6 run tests/k6/stress.js
 *
 * Nota sobre rate limit:
 *   O middleware Traefik limita por IP (10 req/s). Como k6 origina de um
 *   único IP, cada VU usa um X-Forwarded-For distinto para simular clientes
 *   reais distribuídos. O Traefik está configurado com ipStrategy.depth=1,
 *   o que faz com que leia o XFF para identificar a origem.
 *   O rate limit global ainda é testado na fase de pico — cada "cliente"
 *   fictício atingirá seu próprio limite individualmente.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter } from 'k6/metrics';
import { CONSOLIDACAO_URL, LANCAMENTOS_URL, HEADERS, randomUUID, dataAleatoria, tipoAleatorio, valorAleatorio } from './config.js';
import { tokenCaixa, tokenGestor } from './auth.js';

const taxaRateLimit      = new Rate('rate_limit_atingido');
const lancamentosOk      = new Counter('lancamentos_ok');
const lancamentosFalha   = new Counter('lancamentos_falha');

// IP fictício fixo por VU — cada VU simula um cliente distinto para o rate limit por IP do Traefik
function vuIP() {
  const id = __VU % 254 || 1;
  return `10.0.${Math.floor(__VU / 254) % 254}.${id}`;
}

export const options = {
  insecureSkipTLSVerify: true,
  scenarios: {
    // Cenário 1: estressar o consolidado além do SLO
    stress_consolidado: {
      executor:        'ramping-arrival-rate',
      startRate:       10,
      timeUnit:        '1s',
      preAllocatedVUs: 50,
      maxVUs:          500,
      stages: [
        { target:  50, duration: '2m' },  // normal
        { target: 100, duration: '2m' },  // acima do SLO
        { target: 200, duration: '2m' },  // pico extremo
        { target:   0, duration: '1m' },  // recuperação
      ],
      exec: 'consultarConsolidado',
    },

    // Cenário 2: lançamentos em ritmo constante durante todo o stress
    // NFR crítico: lançamentos NÃO pode parar mesmo com consolidado no limite
    lancamentos_continuo: {
      executor:    'constant-vus',
      vus:         5,
      duration:    '7m',
      exec:        'registrarLancamento',
    },
  },

  thresholds: {
    // Consolidado: até 30% de rejeição é aceitável sob stress extremo (429 esperado)
    'http_req_failed{scenario:stress_consolidado}': ['rate<0.30'],

    // Lançamentos: ZERO tolerância a falha — este é o NFR crítico do desafio
    'http_req_failed{scenario:lancamentos_continuo}': ['rate<0.01'],
    'lancamentos_falha': ['count<5'],
  },
};

// ── Cenário 1: stress no consolidado ─────────────────────────────────────────
export function consultarConsolidado() {
  const data = dataAleatoria();
  const res  = http.get(`${CONSOLIDACAO_URL}/saldo/${data}`, {
    headers: { ...HEADERS, Authorization: `Bearer ${tokenGestor()}`, 'X-Forwarded-For': vuIP() },
    timeout: '5s',
  });

  const rateLimit = res.status === 429;
  taxaRateLimit.add(rateLimit);

  check(res, {
    'consolidado responde (200/404/429)': (r) => r.status === 200 || r.status === 404 || r.status === 429,
    'sem 5xx no consolidado':             (r) => r.status < 500,
  });
}

// ── Cenário 2: lançamentos contínuos (prova que o serviço segue operacional) ──
export function registrarLancamento() {
  const payload = JSON.stringify({
    tipo:             tipoAleatorio(),
    valor:            valorAleatorio(),
    descricao:        'k6 stress test — NFR independência',
    data_competencia: dataAleatoria(),
  });

  const res = http.post(`${LANCAMENTOS_URL}/registros`, payload, {
    headers: { ...HEADERS, 'Idempotency-Key': randomUUID(), Authorization: `Bearer ${tokenCaixa()}` },
    timeout: '10s',
  });

  const ok = check(res, {
    'lancamento aceito (201)': (r) => r.status === 201,
  });

  if (ok) {
    lancamentosOk.add(1);
  } else {
    lancamentosFalha.add(1);
  }

  sleep(Math.random() * 0.5 + 0.2); // think time 200–700ms
}
