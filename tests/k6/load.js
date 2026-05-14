/**
 * Load Test — NFR-02: Consolidado 50 req/s com < 5% de erro
 *
 * Valida os requisitos não funcionais do desafio:
 *   NFR-02 → 50 req/s no consolidado com ≤ 5% de falha em pico
 *   NFR-03 → p99 < 200ms no endpoint de consolidado (cache Redis)
 *
 * Fases:
 *   0–2min  → rampa gradual até 50 req/s
 *   2–7min  → sustentado em 50 req/s (janela de medição)
 *   7–8min  → rampa de descida
 *
 * Execução:
 *   k6 run tests/k6/load.js
 *   k6 run --out json=results/load.json tests/k6/load.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { LANCAMENTOS_URL, CONSOLIDACAO_URL, HEADERS, randomUUID, dataAleatoria, tipoAleatorio, valorAleatorio } from './config.js';

// Métricas customizadas para rastrear separadamente por serviço
const consolidadoLatencia = new Trend('consolidado_latencia', true);
const lancamentosLatencia  = new Trend('lancamentos_latencia',  true);
const errosIdempotencia    = new Counter('erros_idempotencia');

export const options = {
  insecureSkipTLSVerify: true,
  scenarios: {
    // Cenário 1: carga no consolidado (NFR-02 — 50 req/s)
    consolidado_leitura: {
      executor:          'ramping-arrival-rate',
      startRate:         1,
      timeUnit:          '1s',
      preAllocatedVUs:   20,
      maxVUs:            100,
      stages: [
        { target: 50, duration: '2m' },   // rampa até 50 req/s
        { target: 50, duration: '5m' },   // sustentado — janela de medição NFR
        { target:  0, duration: '1m' },   // rampa de descida
      ],
      exec: 'consultarConsolidado',
    },

    // Cenário 2: escrita no lançamentos (carga mista realista)
    lancamentos_escrita: {
      executor:    'ramping-vus',
      startVUs:    0,
      stages: [
        { target: 10, duration: '2m' },
        { target: 10, duration: '5m' },
        { target:  0, duration: '1m' },
      ],
      exec: 'registrarLancamento',
    },
  },

  thresholds: {
    // NFR-02: ≤ 5% de erro no consolidado
    'http_req_failed{scenario:consolidado_leitura}': ['rate<0.05'],

    // NFR-03: p99 < 200ms no consolidado (cache Redis quente)
    'consolidado_latencia': ['p(99)<200'],

    // Lançamentos: tolerância maior (escrita com transação + evento)
    'lancamentos_latencia':  ['p(95)<1000'],
    'http_req_failed{scenario:lancamentos_escrita}': ['rate<0.01'],
  },
};

// ── Cenário: consulta de consolidado ──────────────────────────────────────────
export function consultarConsolidado() {
  const data = dataAleatoria();
  const res  = http.get(`${CONSOLIDACAO_URL}/saldo/${data}`, { headers: HEADERS });

  consolidadoLatencia.add(res.timings.duration);

  check(res, {
    'consolidado → 200 ou 404': (r) => r.status === 200 || r.status === 404,
  });
}

// ── Cenário: registro de lançamento ───────────────────────────────────────────
export function registrarLancamento() {
  const idempotencyKey = randomUUID();
  const data           = dataAleatoria();

  const payload = JSON.stringify({
    tipo:             tipoAleatorio(),
    valor:            valorAleatorio(),
    descricao:        'k6 load test',
    data_competencia: data,
  });

  const res = http.post(`${LANCAMENTOS_URL}/registros`, payload, {
    headers: { ...HEADERS, 'Idempotency-Key': idempotencyKey },
  });

  lancamentosLatencia.add(res.timings.duration);

  const ok = check(res, {
    'lancamento criado → 201': (r) => r.status === 201,
  });

  if (!ok && res.status !== 409) {
    // 409 é esperado em idempotência — outros erros contam
  }

  sleep(Math.random() * 0.5 + 0.1); // think time realista: 100–600ms
}
