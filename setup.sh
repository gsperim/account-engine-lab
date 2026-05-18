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

# ── HTTPS Local (mkcert) ──────────────────────────────────────────────────────
#
# mkcert gera certificados assinados por uma CA local.
# `mkcert -install` adiciona a CA ao trust store do sistema, Chrome e Firefox.
# Para Chrome/Firefox no Linux, é necessário `certutil` (pacote libnss3-tools).

CERT_GENERATED=false
CA_INSTALLED=false
HTTPS_NOTE="auto-assinado — use: curl -k"

if command -v mkcert &>/dev/null; then

  # Garante que certutil está disponível para instalar no Chrome/Firefox
  if ! command -v certutil &>/dev/null; then
    info "Instalando libnss3-tools (necessário para Chrome/Firefox)..."
    if sudo apt install -y libnss3-tools &>/dev/null 2>&1; then
      success "libnss3-tools instalado."
    else
      warn "Não foi possível instalar libnss3-tools — CA não será adicionada ao Chrome/Firefox."
      warn "Rode manualmente: sudo apt install libnss3-tools && mkcert -install"
    fi
  fi

  # Instala a CA local no sistema e nos browsers
  info "Instalando CA do mkcert no sistema e browsers..."
  if mkcert -install &>/dev/null 2>&1; then
    success "CA do mkcert instalada — Chrome e Firefox confiarão no certificado."
    CA_INSTALLED=true
  else
    warn "mkcert -install falhou — verifique permissões."
  fi

  # Gera o certificado para localhost (se ainda não existe)
  if [ -f "traefik/certs/local.pem" ]; then
    success "Certificado mkcert já existe — HTTPS com CA local."
  else
    info "Gerando certificado para localhost..."
    mkcert \
      -cert-file traefik/certs/local.pem \
      -key-file  traefik/certs/local-key.pem \
      localhost 127.0.0.1
    success "Certificado gerado em traefik/certs/"
    CERT_GENERATED=true
  fi

  if [ "$CA_INSTALLED" = "true" ]; then
    HTTPS_NOTE="CA local instalada — browser confia"
  else
    HTTPS_NOTE="CA local — rode: mkcert -install"
  fi

else
  warn "mkcert não encontrado — Traefik usará certificado auto-assinado."
fi

# ── Subir serviços ────────────────────────────────────────────────────────────

info "Iniciando infraestrutura..."
$COMPOSE pull --quiet
$COMPOSE up -d

# Se certs foram gerados agora, reinicia o Traefik para carregá-los
if [ "$CERT_GENERATED" = "true" ]; then
  info "Recarregando Traefik com o novo certificado..."
  $COMPOSE restart traefik
fi

info "Iniciando serviços de aplicação (build pode demorar)..."
$COMPOSE --profile app up -d --build

# ── Aguardar disponibilidade ──────────────────────────────────────────────────

info "Aguardando serviços ficarem disponíveis..."

wait_for() {
  local url=$1 name=$2 max=${3:-30} attempts=0
  while ! curl -sf --insecure "$url" &>/dev/null; do
    attempts=$((attempts + 1))
    if [ $attempts -ge $max ]; then
      warn "$name demorou mais que o esperado — verifique com: $COMPOSE logs"
      return
    fi
    sleep 1
  done
  success "$name disponível"
}

wait_for "http://localhost:8000"  "Portal de Documentação"
wait_for "http://localhost:8080"  "Diagramas C4 (Structurizr)"
wait_for "http://localhost:8091"  "Traefik Dashboard"
wait_for "http://localhost:3000"  "Grafana"
wait_for "http://localhost:8090/lancamentos/actuator/health"  "Serviço de Lançamentos" 120
wait_for "http://localhost:8090/consolidacao/actuator/health" "Serviço de Consolidação" 120

# ── Resumo ────────────────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Serviços ativos:${RESET}"
echo ""
echo -e "  ${GREEN}●${RESET} API Gateway (HTTP)        →  http://localhost:8090"
echo -e "  ${GREEN}●${RESET} API Gateway (HTTPS)       → https://localhost:8443   ${YELLOW}${HTTPS_NOTE}${RESET}"
echo -e "  ${GREEN}●${RESET} Traefik Dashboard         →  http://localhost:8091"
echo -e "  ${GREEN}●${RESET} Keycloak (IdP)            →  http://localhost:8180"
echo -e "  ${GREEN}●${RESET} RabbitMQ Management       →  http://localhost:15672"
echo -e "  ${GREEN}●${RESET} Grafana                   →  http://localhost:3000   ${YELLOW}admin / admin${RESET}"
echo -e "  ${GREEN}●${RESET} Prometheus                →  http://localhost:9090"
echo -e "  ${GREEN}●${RESET} Portal de Documentação    →  http://localhost:8000"
echo -e "  ${GREEN}●${RESET} Diagramas C4              →  http://localhost:8080"
echo -e "  ${GREEN}●${RESET} Swagger UI (contratos)    →  http://localhost:8070"
echo ""

if ! command -v mkcert &>/dev/null; then
  echo -e "${YELLOW}Dica — HTTPS confiável no browser:${RESET}"
  echo "  1. Instale mkcert → https://github.com/FiloSottile/mkcert#installation"
  echo "  2. Execute:          bash setup.sh  (instala a CA automaticamente)"
  echo ""
elif [ "$CA_INSTALLED" = "true" ] && [ "$CERT_GENERATED" = "true" ]; then
  echo -e "${YELLOW}Dica — CA instalada agora pela primeira vez:${RESET}"
  echo "  Reinicie o Chrome/Firefox para que reconheça o certificado."
  echo ""
fi

echo -e "Para parar: ${BOLD}$COMPOSE down${RESET}"
echo ""
