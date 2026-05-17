/**
 * Chaos Test — carga contínua para os experimentos de Chaos Engineering
 *
 * Mantém 20 req/s no consolidado + 5 VUs em lançamentos por 5 minutos.
 * Janela curta suficiente para injetar e observar cada experimento.
 *
 * Execução:
 *   k6 run tests/k6/chaos.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { LANCAMENTOS_URL, CONSOLIDACAO_URL, HEADERS, randomUUID, dataAleatoria, tipoAleatorio, valorAleatorio } from './config.js';
import { tokenCaixa, tokenGestor } from './auth.js';

const consolidadoLatencia = new Trend('consolidado_latencia', true);
const lancamentosLatencia  = new Trend('lancamentos_latencia',  true);
const errosTotal           = new Counter('chaos_erros_total');

export const options = {
  insecureSkipTLSVerify: true,
  scenarios: {
    consolidado_leitura: {
      executor:        'constant-arrival-rate',
      rate:            20,
      timeUnit:        '1s',
      duration:        '5m',
      preAllocatedVUs: 10,
      maxVUs:          50,
      exec:            'consultarConsolidado',
    },
    lancamentos_escrita: {
      executor: 'constant-vus',
      vus:      5,
      duration: '5m',
      exec:     'registrarLancamento',
    },
  },
  thresholds: {
    // Limites sob caos — tolerância maior que o load test normal
    'http_req_failed{scenario:consolidado_leitura}': ['rate<0.05'],
    'http_req_failed{scenario:lancamentos_escrita}':  ['rate<0.01'],
  },
};

export function consultarConsolidado() {
  const res = http.get(`${CONSOLIDACAO_URL}/saldo/${dataAleatoria()}`, {
    headers: { ...HEADERS, Authorization: `Bearer ${tokenGestor()}` },
  });
  consolidadoLatencia.add(res.timings.duration);
  const ok = check(res, { 'consolidado → 200 ou 404': r => r.status === 200 || r.status === 404 });
  if (!ok) errosTotal.add(1);
}

export function registrarLancamento() {
  const res = http.post(`${LANCAMENTOS_URL}/registros`,
    JSON.stringify({ tipo: tipoAleatorio(), valor: valorAleatorio(), descricao: 'chaos test', data_competencia: dataAleatoria() }),
    { headers: { ...HEADERS, 'Idempotency-Key': randomUUID(), Authorization: `Bearer ${tokenCaixa()}` } }
  );
  lancamentosLatencia.add(res.timings.duration);
  const ok = check(res, { 'lancamento → 201': r => r.status === 201 });
  if (!ok && res.status !== 409) errosTotal.add(1);
}
