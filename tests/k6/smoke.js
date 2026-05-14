/**
 * Smoke Test — Fluxo de Caixa
 *
 * Objetivo: verificar que os dois serviços respondem corretamente
 * com carga mínima antes de executar os testes de carga real.
 *
 * Execução:
 *   k6 run tests/k6/smoke.js
 *   k6 run -e BASE_URL=https://localhost:8443 tests/k6/smoke.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { LANCAMENTOS_URL, CONSOLIDACAO_URL, HEADERS, randomUUID, dataAleatoria, tipoAleatorio, valorAleatorio } from './config.js';

export const options = {
  vus:                    2,
  duration:               '30s',
  insecureSkipTLSVerify:  true,

  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const idempotencyKey = randomUUID();
  const data           = dataAleatoria();

  // ── POST /lancamentos/registros ────────────────────────────────────────────
  const payload = JSON.stringify({
    tipo:             tipoAleatorio(),
    valor:            valorAleatorio(),
    descricao:        'Teste k6 smoke',
    data_competencia: data,
  });

  const resPost = http.post(`${LANCAMENTOS_URL}/registros`, payload, {
    headers: { ...HEADERS, 'Idempotency-Key': idempotencyKey },
  });

  check(resPost, {
    'POST /lancamentos/registros → 201': (r) => r.status === 201,
    'POST tem campo id':                 (r) => JSON.parse(r.body).id !== undefined,
  });

  if (resPost.status === 201) {
    const lancamentoId = JSON.parse(resPost.body).id;

    // ── GET /lancamentos/registros/{id} ───────────────────────────────────────
    const resGet = http.get(`${LANCAMENTOS_URL}/registros/${lancamentoId}`, { headers: HEADERS });
    check(resGet, {
      'GET /lancamentos/registros/{id} → 200': (r) => r.status === 200,
    });
  }

  // ── GET /consolidacao/saldo/{data} ────────────────────────────────────────
  // 404 é resposta válida para datas sem lançamentos — não deve inflar http_req_failed
  const resConsolidado = http.get(`${CONSOLIDACAO_URL}/saldo/${data}`, {
    headers: HEADERS,
    responseCallback: http.expectedStatuses(200, 404),
  });
  check(resConsolidado, {
    'GET /consolidacao/saldo/{data} → 200 ou 404': (r) => r.status === 200 || r.status === 404,
  });

  // ── Idempotência: mesma chave → 409 ──────────────────────────────────────────
  // responseCallback marca 409 como esperado para não inflar http_req_failed
  const resIdem = http.post(`${LANCAMENTOS_URL}/registros`, payload, {
    headers: { ...HEADERS, 'Idempotency-Key': idempotencyKey },
    responseCallback: http.expectedStatuses(201, 409),
  });
  check(resIdem, {
    'POST duplicado → 409': (r) => r.status === 409,
  });

  sleep(1);
}
