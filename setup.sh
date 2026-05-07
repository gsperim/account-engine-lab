#!/usr/bin/env bash
# setup.sh — Configura e inicia o projeto Controle de Fluxo de Caixa Diário.
#
# Uso direto:
#   bash setup.sh
#
# Uso via curl (após publicar o repositório):
#   curl -fsSL https://raw.githubusercontent.com/gsperim/desafio-carrefour/main/setup.sh | bash

set -euo pipefail

REPO_URL="https://github.com/gsperim/desafio-carrefour.git"
REPO_DIR="desafio-carrefour"
BOLD="\033[1m"
GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[0;33m"
RESET="\033[0m"

info()    { echo -e "${BOLD}▶ $*${RESET}"; }
success() { echo -e "${GREEN}✓ $*${RESET}"; }
warn()    { echo -e "${YELLOW}⚠ $*${RESET}"; }
error()   { echo -e "${RED}✗ $*${RESET}"; exit 1; }

echo ""
echo -e "${BOLD}Controle de Fluxo de Caixa Diário${RESET}"
echo "────────────────────────────────────────────────────"
echo ""

# ── Pré-requisitos ────────────────────────────────────────────────────────────

info "Verificando pré-requisitos..."

if ! command -v docker &>/dev/null; then
  error "Docker não encontrado. Instale em: https://docs.docker.com/get-docker/"
fi
success "Docker $(docker --version | awk '{print $3}' | tr -d ',')"

if docker compose version &>/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose &>/dev/null; then
  COMPOSE="docker-compose"
else
  error "Docker Compose não encontrado. Instale em: https://docs.docker.com/compose/install/"
fi
success "Docker Compose ($COMPOSE)"

# ── Repositório ───────────────────────────────────────────────────────────────

# Se já está dentro do repositório clonado, não clona novamente
if [ -f "docker-compose.yml" ] && [ -f "mkdocs.yml" ]; then
  info "Repositório detectado no diretório atual."
else
  if [ -d "$REPO_DIR" ]; then
    info "Diretório $REPO_DIR já existe — atualizando..."
    cd "$REPO_DIR"
    git pull --ff-only || warn "Não foi possível atualizar — continuando com versão local."
  else
    info "Clonando repositório..."
    git clone "$REPO_URL" "$REPO_DIR"
    cd "$REPO_DIR"
  fi
  success "Repositório pronto."
fi

# ── Subir serviços ────────────────────────────────────────────────────────────

info "Iniciando serviços..."
$COMPOSE pull --quiet
$COMPOSE up -d

# ── Aguardar disponibilidade ──────────────────────────────────────────────────

info "Aguardando serviços ficarem disponíveis..."

wait_for() {
  local url=$1 name=$2 attempts=0 max=30
  while ! curl -sf "$url" &>/dev/null; do
    attempts=$((attempts + 1))
    if [ $attempts -ge $max ]; then
      warn "$name demorou mais que o esperado — verifique com: $COMPOSE logs"
      return
    fi
    sleep 1
  done
  success "$name disponível em $url"
}

wait_for "http://localhost:8000" "Portal de Documentação"
wait_for "http://localhost:8080" "Diagramas C4 (Structurizr)"

# ── Resumo ────────────────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Serviços ativos:${RESET}"
echo ""
echo -e "  ${GREEN}●${RESET} Portal de Documentação   → http://localhost:8000"
echo -e "  ${GREEN}●${RESET} Diagramas C4              → http://localhost:8080"
echo ""
echo -e "Para parar: ${BOLD}$COMPOSE down${RESET}"
echo ""
