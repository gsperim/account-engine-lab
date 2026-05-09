# Controle de Fluxo de Caixa Diário

Sistema de controle financeiro para comerciantes com dois serviços independentes:

- **Serviço de Lançamentos** — registra débitos e créditos em tempo real
- **Serviço de Consolidação Diária** — mantém o saldo consolidado por dia, disponível para consulta a qualquer momento

O NFR crítico do sistema: o registro de lançamentos **nunca pode ser interrompido** por falha no serviço de consolidação. Os dois serviços são desacoplados por design via mensageria assíncrona.

> **Estado atual:** fase de arquitetura e documentação. Os serviços de aplicação estão em especificação — o que está disponível para execução é o portal de documentação e os diagramas de arquitetura.

---

## Início Rápido

### Um único comando

```bash
curl -fsSL https://raw.githubusercontent.com/gsperim/desafio-carrefour/main/setup.sh | bash
```

O script verifica os pré-requisitos, clona o repositório e sobe os serviços automaticamente.

### Manualmente

```bash
git clone https://github.com/gsperim/desafio-carrefour.git
cd desafio-carrefour
docker compose up
```

---

## Pré-requisitos

| Ferramenta | Versão mínima | Verificar |
|------------|--------------|-----------|
| Docker | 24+ | `docker --version` |
| Docker Compose | v2 (plugin) | `docker compose version` |

---

## Serviços

| Serviço | URL | Descrição |
|---------|-----|-----------|
| **API Gateway (HTTP)** | http://localhost:8090 | Entrada da aplicação |
| **API Gateway (HTTPS)** | https://localhost:8443 | Entrada da aplicação com TLS |
| Traefik Dashboard | http://localhost:8091 | Rotas e middlewares ativos |
| RabbitMQ Management | http://localhost:15672 | Filas e mensagens em trânsito |
| Portal de Documentação | http://localhost:8000 | Arquitetura, requisitos, decisões e planejamento |
| Diagramas C4 | http://localhost:8080 | Contexto do sistema e containers (Structurizr Lite) |

```bash
# Subir infraestrutura + ferramentas de documentação (fase atual)
docker compose up

# Subir tudo incluindo serviços de aplicação (Etapa 7+)
docker compose --profile app up

# Subir em background
docker compose up -d

# Parar
docker compose down

# Escalar Consolidação para 3 réplicas (NFR-02)
docker compose --profile app up --scale consolidado=3

# Subir apenas a documentação
docker compose up docs

# Subir apenas os diagramas C4
docker compose up structurizr
```

---

## HTTPS Local

Por padrão o gateway usa um certificado auto-assinado — `curl -k https://localhost:8443` funciona, mas o browser exibe aviso de segurança. Para HTTPS com certificado confiável no browser, use [mkcert](https://github.com/FiloSottile/mkcert):

```bash
# 1. Instalar mkcert (uma vez por máquina)
#    macOS:  brew install mkcert
#    Linux:  apt install mkcert  ou  https://github.com/FiloSottile/mkcert/releases
#    Windows: choco install mkcert

# 2. Instalar a CA local no sistema e browsers (uma vez por máquina)
mkcert -install

# 3. Gerar certificado para localhost
mkcert -cert-file traefik/certs/local.pem \
       -key-file  traefik/certs/local-key.pem \
       localhost 127.0.0.1

# 4. Recarregar o Traefik
docker compose restart traefik
```

Após isso, `https://localhost:8443` abre sem aviso no browser. Os arquivos `.pem` estão no `.gitignore` — cada desenvolvedor gera os seus localmente.

---

## Estrutura do Repositório

```
.
├── docs/                    # Portal de documentação (MkDocs Material)
│   ├── negocio/             # Domínio, requisitos, drivers, princípios
│   ├── planejamento.md      # Racional e fases do projeto
│   ├── stack.md             # Ferramentas e decisões de plataforma
│   └── adr/                 # Architecture Decision Records
├── structurizr/
│   └── workspace.dsl        # Diagramas C4 (System Context + Containers)
├── scripts/
│   └── validate_docs.py     # Validação de consistência da documentação
├── docker-compose.yml
├── CONTRIBUTING.md          # Git Flow, Conventional Commits, SemVer
└── setup.sh                 # Script de setup completo
```

---

## Documentação

O portal em http://localhost:8000 contém:

| Seção | Conteúdo |
|-------|----------|
| **Visão Executiva** | Problema de negócio, jornada do usuário e valor entregue |
| **Drivers e Stakeholders** | Dores mapeadas e rastreabilidade até os componentes |
| **Principles Catalog** | 8 princípios arquiteturais que guiam todas as decisões |
| **Domínios e Value Stream** | Bounded contexts, capacidades e fluxo de valor |
| **Requisitos** | RF-01 a RF-05 detalhados + NFRs com critérios de aceite |
| **Planejamento** | Racional das fases e decisões que moldaram o projeto |
| **Stack** | Ferramentas escolhidas e justificativas |
| **ADRs** | Architecture Decision Records |

---

## Desenvolvimento

Consulte o [CONTRIBUTING.md](CONTRIBUTING.md) para padrões de branches, commits e versionamento.

```bash
# Configurar ambiente Python (para rodar MkDocs localmente sem Docker)
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
mkdocs serve

# Validar consistência da documentação
python3 scripts/validate_docs.py
```
