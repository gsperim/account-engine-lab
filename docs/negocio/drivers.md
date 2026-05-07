---
tags:
  - negocio
  - stakeholders
---

# Registro de Drivers e Stakeholders

**Papéis:** 💼 Arquiteto de Negócios · 🏛️ Arquiteto Corporativo
**Framework:** ArchiMate — Motivation View

---

## Por que estes artefatos?

Antes de qualquer diagrama técnico, precisamos responder três perguntas: *para quem*, *por que* e *o que não pode falhar*. Os artefatos desta página respondem exatamente isso.

**Motivation View — ArchiMate**
O ArchiMate reserva uma camada inteira para motivação antes de falar em serviços ou componentes. Essa ordem importa: sistemas construídos sem clareza de motivação acumulam funcionalidades que ninguém pediu e carecem das que realmente importam. Começar pelo "por que" é a diferença entre arquitetura e engenharia de software.

**Stakeholder Map**
Decisões arquiteturais servem pessoas, não tecnologias. Mapear quem são os stakeholders e o que os preocupa evita o erro de projetar para o stakeholder errado — ou de ignorar que uma decisão técnica tem consequência operacional para um time que não estava na sala. Cada linha da tabela representa uma perspectiva que precisa ser atendida.

**Registro de Drivers com cadeia de rastreabilidade**
A cadeia *Dor → Requisito → Decisão Arquitetural → Componente* serve a um propósito simples: nenhum componente do sistema deve existir sem uma dor de negócio como origem. Se um elemento da arquitetura não consegue ser rastreado até um driver, sua existência pode ser questionada. Inversamente, toda dor identificada deve gerar um requisito — e todo requisito deve chegar a um componente concreto. Lacunas nessa cadeia são riscos de escopo.

---

## Stakeholder Map

| Stakeholder | Papel | Interesse Principal | Preocupação |
|-------------|-------|--------------------|-----------  |
| Comerciante | Usuário final do sistema | Controlar o fluxo de caixa com precisão e em tempo real | Disponibilidade e confiabilidade dos dados financeiros |
| Equipe de Operações | Operação e suporte do sistema | Estabilidade, desempenho e rastreabilidade de incidentes | Independência entre serviços em caso de falha |
| Instituição Financeira | Patrocinador e dono do produto | Produto financeiro que gere valor ao comerciante parceiro | Escalabilidade, segurança e conformidade regulatória |

---

## Registro de Drivers

Rastreabilidade de dores de negócio até os componentes que as resolvem.
Cadeia: **Dor → Requisito → Decisão Arquitetural → Componente**

| ID | Dor / Driver | Stakeholder | Categoria | Requisito Gerado | ADR | Componente |
|----|-------------|-------------|-----------|-----------------|-----|------------|
| <span id="d-01"></span>D-01 | Comerciante não tem registro estruturado das entradas e saídas do caixa — controle é manual e sujeito a erros | Comerciante | Negócio | [RF-01](requisitos.md#rf-01), [RF-02](requisitos.md#rf-02), [RF-05](requisitos.md#rf-05), [RF-08](requisitos.md#rf-08) | — | Serviço de Lançamentos |
| <span id="d-02"></span>D-02 | Impossibilidade de visualizar o saldo do dia sem processar manualmente todos os lançamentos | Comerciante | Negócio | [RF-03](requisitos.md#rf-03), [RF-09](requisitos.md#rf-09) | — | Serviço de Consolidação Diária |
| <span id="d-03"></span>D-03 | Indisponibilidade do sistema de consolidação não pode impedir o registro de novos lançamentos — risco operacional crítico | Operação | Resiliência | [NFR-01](requisitos.md#nfr-01) | [ADR-001](adr/ADR-001-padrao-arquitetural.md) · [ADR-002](adr/ADR-002-message-broker.md) | Mensageria assíncrona |
| <span id="d-04"></span>D-04 | Em dias de pico, a consolidação precisa absorver 50 requisições por segundo com no máximo 5% de perda | Operação / Negócio | Performance | [NFR-02](requisitos.md#nfr-02), [NFR-04](requisitos.md#nfr-04), [NFR-07](requisitos.md#nfr-07) | A definir — estratégia de escalabilidade | Serviço de Consolidação Diária + Cache |
| <span id="d-05"></span>D-05 | Lançamentos financeiros não podem ser perdidos mesmo em cenários de falha parcial do sistema | Operação | Confiabilidade | [NFR-03](requisitos.md#nfr-03), [RF-06](requisitos.md#rf-06) | [ADR-003](adr/ADR-003-outbox-pattern.md) | Mensageria + persistência |
| <span id="d-06"></span>🔹 D-06 | Operações financeiras precisam de rastreabilidade completa para auditoria e conformidade regulatória | Institucional / Operação | Compliance | [NFR-09](requisitos.md#nfr-09) | A definir — estratégia de auditoria | Serviço de Lançamentos + Serviço de Consolidação Diária |
| <span id="d-07"></span>🔹 D-07 | Em cenário de falha catastrófica da Consolidação, o estado deve ser recuperável sem acesso direto ao banco do Lançamentos | Operação | Resiliência | [NFR-10](requisitos.md#nfr-10), [RF-07](requisitos.md#rf-07) | A definir — estratégia de recovery | Serviço de Lançamentos |
| <span id="d-08"></span>🔹 D-08 | Dados pessoais e financeiros dos comerciantes estão sujeitos à LGPD e exigem conformidade com retenção e direito de exclusão | Institucional | Compliance | [C-04](requisitos.md#c-04) | A definir — estratégia de dados e privacidade | Serviços de Lançamentos + Consolidação Diária |
