---
tags:
  - planejamento
---

# Planejamento e Racional do Projeto

**Papéis:** 🧩 Arquiteto de Soluções · 💼 Arquiteto de Negócios · 🏛️ Arquiteto Corporativo

---

## Por onde começar

A primeira decisão de qualquer projeto de arquitetura é sobre ordem: o que precisa ser entendido antes de qualquer diagrama técnico ser desenhado? A resposta, neste projeto, foi clara — o domínio de negócio.

Antes de escolher tecnologias, definir serviços ou esboçar integrações, era necessário entender quem tem dores, quais são essas dores e o que o sistema precisa ser capaz de fazer para resolvê-las. Arquitetura desenhada sem essa base tende a resolver os problemas errados com elegância técnica.

Por isso, o projeto foi estruturado em uma progressão deliberada: do negócio para a solução, da solução para a infraestrutura, da infraestrutura para a implementação. Cada fase só começa quando a anterior produziu decisões suficientemente estáveis para servir de base.

---

## Abordagem de documentação

A documentação arquitetural deste projeto usa dois frameworks complementares:

**ArchiMate** para as camadas de motivação e negócio — capturando por que o sistema existe, quem são os stakeholders, quais são os drivers e como as capacidades se organizam. Os *conceitos* do ArchiMate são aplicados integralmente; os diagramas são produzidos em Mermaid para manter tudo versionável em texto, sem dependência de ferramenta externa.

**C4 Model** para a decomposição técnica — do contexto do sistema (L1) até os componentes internos (L3). O Structurizr DSL, disponível via `docker-compose`, garante que os diagramas C4 sejam docs-as-code: versionados, revisáveis e reproduzíveis.

O TOGAF foi usado como referência metodológica parcial. As fases A a D e F do ADM orientaram a sequência de trabalho, sem a cerimônia do framework completo — que seria desproporcional para o escopo deste projeto.

---

## As fases do projeto

### Fase 1 — Domínio de Negócio ✓

A fase mais importante do projeto, e por isso a mais longa. Aqui foram estabelecidas as bases que todas as fases seguintes consumirão.

O trabalho começou pelo mapeamento dos *drivers* — as dores reais que justificam o sistema. Cada driver gerou requisitos; cada requisito foi rastreado até um componente ou decisão arquitetural. Essa cadeia de rastreabilidade não é burocracia: é proteção contra escopo arbitrário e garantia de que nada foi construído sem uma necessidade de negócio como origem.

Em paralelo, os domínios foram classificados estrategicamente. Gestão de Fluxo de Caixa é o *core domain* — onde o esforço de design precisa ser máximo. Identidade e Acesso é *supporting* — importante, mas não diferenciador, candidato a solução de prateleira. Infraestrutura é *generic* — commodity.

Essa classificação teve consequência direta: toda a profundidade de modelagem foi concentrada no core domain. Os outros domínios foram definidos em linhas gerais e delegados a soluções existentes.

Os requisitos funcionais foram detalhados com campos de entrada e saída, regras de negócio, casos de borda e critérios de aceite. Os NFRs foram elevados à mesma importância — particularmente o NFR crítico que diz que o registro de lançamentos não pode ser afetado pela queda do serviço de consolidação. Essa restrição de resiliência moldou toda a arquitetura subsequente.

**Artefatos produzidos:** [Visão Executiva](negocio/visao-executiva.md) · [Drivers e Stakeholders](negocio/drivers.md) · [Principles Catalog](negocio/principios.md) · [Domínios, Value Stream e Capacidades](negocio/dominios.md) · [Glossário](glossario.md) · [Requisitos](negocio/requisitos.md)

---

### Fase 2 — Arquitetura da Solução ✓

Com o domínio estabelecido, esta fase respondeu à pergunta central de design: como o sistema é estruturado para atender os requisitos funcionais e, especialmente, os não funcionais?

A decisão arquitetural mais importante foi o padrão de desacoplamento entre os dois serviços. O [NFR-01](negocio/requisitos.md#nfr-01) exige que o serviço de lançamentos seja independente do serviço de consolidação — o que determina comunicação assíncrona via broker de mensagens, formalizada no [ADR-001](adr/ADR-001-padrao-arquitetural.md). A partir daí, cada decisão subsequente derivou dessa escolha central.

O C4 L1 e L2 foram evoluídos iterativamente à medida que as decisões amadureciam: dois atores distintos (Caixa e Gestor) refletem jornadas diferentes; API Gateway centraliza autenticação, rate limiting e roteamento; PDV aparece como sistema externo alternativo ao frontend; dois bancos isolados e Redis de cache completam a topologia de containers.

Os contratos de integração foram documentados antes do código — espírito do **Spec-Driven Development** — com schemas JSON dos eventos de domínio e tabela de endpoints REST. Na Etapa 7, esses contratos Markdown serão convertidos em `openapi.yaml` e `asyncapi.yaml` para dirigir a implementação.

**Artefatos produzidos:**

| Artefato | Link |
|----------|------|
| Diagrama C4 L1 e L2 | [structurizr/workspace.dsl](https://github.com) · visualizar via `docker-compose up structurizr` |
| ADR-001 — Padrão Arquitetural: Microserviços Orientados a Eventos | [adr/ADR-001](adr/ADR-001-padrao-arquitetural.md) |
| ADR-002 — Message Broker: RabbitMQ | [adr/ADR-002](adr/ADR-002-message-broker.md) |
| ADR-003 — Garantia de Entrega: Transactional Outbox Pattern | [adr/ADR-003](adr/ADR-003-outbox-pattern.md) |
| ADR-004 — Validação de Tokens: JWT com Validação Local via JWKS | [adr/ADR-004](adr/ADR-004-jwt-validacao-local.md) |
| ADR-005 — Protocolo de Comunicação Interna: REST sobre gRPC | [adr/ADR-005](adr/ADR-005-protocolo-comunicacao-interna.md) |
| Contratos de Integração (eventos e APIs REST) | [engenharia/contratos](engenharia/contratos.md) |
| Arquitetura de Transição *(diferencial)* | [engenharia/transicao](engenharia/transicao.md) |

---

### Fase 3 — Infraestrutura e Plataforma

Define como o sistema é executado: ambiente de containers, estratégia de escalabilidade horizontal para absorver os picos exigidos pelo NFR-02, topologia de rede e isolamento de componentes. A execução local via `docker-compose` é um requisito obrigatório do desafio e será atendida aqui.

---

### Fase 4 — Dados e Persistência

Cada serviço possui e controla exclusivamente seus próprios dados — esse princípio (*database per service*) foi decidido na Fase 1. Esta fase o implementa: define os modelos de dados por serviço, escolhe os mecanismos de persistência com justificativa e formaliza os contratos dos eventos de domínio.

A estratégia de consistência eventual entre os serviços, que foi aceita como trade-off na Fase 1, também é detalhada aqui.

---

### Fase 5 — Segurança

Define o modelo de autenticação e autorização, a estratégia de proteção de dados em trânsito e em repouso, e o mapeamento da superfície de ataque. O princípio *segurança por design* estabelecido na Fase 1 é operacionalizado.

---

### Fase 6 — Observabilidade e Monitoramento

Logs estruturados, métricas e rastreamento distribuído são requisitos do sistema, não opcionais — isso foi decidido na Fase 1 como NFR-04. Esta fase define os três pilares, os SLOs, as ferramentas e a estratégia de alertas.

---

### Fase 7 — Implementação

Com toda a arquitetura documentada e as decisões formalizadas em ADRs, a implementação começa com contexto completo seguindo o fluxo **Spec-Driven Development**: os contratos Markdown definidos na Fase 2 são convertidos em `openapi.yaml` (REST) e `asyncapi.yaml` (eventos), gerando stubs de servidor, mocks e validação de contrato automatizada no CI.

Os serviços de Lançamentos e Consolidação Diária são implementados contra esses contratos. É também nesta fase que o DDD Tático entra: aggregates, entities, value objects e domain events são modelados dentro de cada bounded context.

> Base para o `openapi.yaml` e `asyncapi.yaml`: [Contratos de Integração](engenharia/contratos.md)

---

### Fase 8 — Pipeline e Entrega

Containerização, CI/CD e instruções de execução local. O `docker-compose` que permite rodar todo o sistema sem dependências externas é entregue aqui.

---

### Fase 9 — Documentação Final

Consolidação de todos os artefatos, revisão do README, registro de pontos não implementados e evoluções futuras. O repositório público no GitHub é o artefato final desta fase.

---

## Decisões que moldaram o projeto

Algumas decisões tomadas cedo tiveram impacto em cascata sobre todas as fases seguintes:

**O desacoplamento assíncrono entre lançamentos e consolidação** foi a decisão central. Ela saiu de um requisito de resiliência — não de uma preferência técnica. O broker de mensagens não é uma escolha de stack; é a consequência direta de um requisito de negócio.

**A imutabilidade dos lançamentos** simplificou o modelo de dados e eliminou toda uma categoria de problemas de concorrência. Lançamentos são append-only; correções são novos lançamentos compensatórios.

**A consistência eventual foi aceita** como trade-off consciente: o saldo consolidado pode estar momentaneamente desatualizado, e isso é aceitável. O que não é aceitável é perder um lançamento. Essa hierarquia de prioridades — confiabilidade antes de consistência em tempo real — guiou todas as decisões de persistência e mensageria.
