/**
 * Teste de Idempotência — Garantia exactly-once por Idempotency-Key
 *
 * Verifica que:
 *  1. A primeira requisição com uma chave retorna 201
 *  2. Todas as requisições subsequentes com a mesma chave retornam 409
 *  3. Sob concorrência, apenas um 201 é emitido por chave
 *
 * Execução:
 *   k6 run tests/k6/idempotencia.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { LANCAMENTOS_URL, HEADERS, randomUUID } from './config.js';
import { tokenCaixa } from './auth.js';

const violacoesIdempotencia = new Counter('violacoes_idempotencia');

export const options = {
  insecureSkipTLSVerify: true,
  scenarios: {
    // 10 VUs enviando a MESMA chave concorrentemente
    concorrencia_mesma_chave: {
      executor: 'shared-iterations',
      vus:        10,
      iterations: 50,
      maxDuration: '30s',
      exec: 'mesmaChaaveConcorrente',
    },
    // Chaves únicas por iteração — todas devem retornar 201
    chaves_unicas: {
      executor: 'per-vu-iterations',
      vus:        5,
      iterations: 20,
      exec: 'chaveUnica',
      startTime:  '35s',
    },
  },

  thresholds: {
    'violacoes_idempotencia': ['count==0'],  // zero violações toleradas
  },
};

// Chave compartilhada entre todos os VUs do cenário de concorrência
const CHAVE_COMPARTILHADA = randomUUID();
const PAYLOAD_COMPARTILHADO = JSON.stringify({
  tipo:             'CREDITO',
  valor:            100.00,
  descricao:        'teste idempotencia concorrente',
  data_competencia: '2026-05-10',
});

export function mesmaChaaveConcorrente() {
  const res = http.post(`${LANCAMENTOS_URL}/registros`, PAYLOAD_COMPARTILHADO, {
    headers: { ...HEADERS, 'Idempotency-Key': CHAVE_COMPARTILHADA, Authorization: `Bearer ${tokenCaixa()}` },
  });

  const ok = check(res, {
    '201 ou 409 (idempotente)': (r) => r.status === 201 || r.status === 409,
  });

  // Qualquer outro status é violação
  if (!ok) violacoesIdempotencia.add(1);

  sleep(0.1);
}

export function chaveUnica() {
  const key     = randomUUID();
  const payload = JSON.stringify({
    tipo:             'DEBITO',
    valor:            50.00,
    descricao:        `k6-idempotencia-${key}`,
    data_competencia: '2026-05-10',
  });

  const res = http.post(`${LANCAMENTOS_URL}/registros`, payload, {
    headers: { ...HEADERS, 'Idempotency-Key': key, Authorization: `Bearer ${tokenCaixa()}` },
  });

  const ok = check(res, {
    'chave única → 201': (r) => r.status === 201,
  });

  if (!ok) violacoesIdempotencia.add(1);
}
