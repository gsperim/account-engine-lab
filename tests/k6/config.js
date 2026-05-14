// Configuração compartilhada entre todos os scripts k6
// Uso: import { BASE_URL, HEADERS, randomUUID, dataAleatoria } from './config.js';

export const BASE_URL         = __ENV.BASE_URL || 'https://localhost:8443';
export const LANCAMENTOS_URL  = `${BASE_URL}/lancamentos`;
export const CONSOLIDACAO_URL = `${BASE_URL}/consolidacao`;

// URL do Keycloak via Traefik — usada pelo módulo auth.js
// Sobrescrever com: k6 run -e AUTH_URL=http://... script.js
export const AUTH_URL = __ENV.AUTH_URL ||
  `${BASE_URL}/auth/realms/fluxocaixa/protocol/openid-connect/token`;

export const HEADERS = {
  'Content-Type': 'application/json',
  'Accept':       'application/json',
};

// k6 não tem crypto nativo — UUID v4 gerado por string aleatória
export function randomUUID() {
  const s = () => Math.floor(Math.random() * 0x10000).toString(16).padStart(4, '0');
  return `${s()}${s()}-${s()}-4${s().slice(1)}-${['8','9','a','b'][Math.floor(Math.random()*4)]}${s().slice(1)}-${s()}${s()}${s()}`;
}

// Retorna data no formato YYYY-MM-DD nos últimos 30 dias
export function dataAleatoria() {
  const dias = Math.floor(Math.random() * 30);
  const d    = new Date();
  d.setDate(d.getDate() - dias);
  return d.toISOString().split('T')[0];
}

export const TIPOS = ['CREDITO', 'DEBITO'];

export function tipoAleatorio() {
  return TIPOS[Math.floor(Math.random() * TIPOS.length)];
}

export function valorAleatorio() {
  return parseFloat((Math.random() * 9999 + 0.01).toFixed(2));
}
