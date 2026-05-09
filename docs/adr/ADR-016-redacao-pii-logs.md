---
tags:
  - adr
  - decisao
  - seguranca
  - observabilidade
  - lgpd
---

# ADR-016 — Redação de PII no Pipeline de Observabilidade

**Papéis:** 🔒 Arquiteto de Segurança · 👁️ Arquiteto de Observabilidade · 📊 Arquiteto de Dados  
**Data:** 2026-05-09  
**Status:** Aceito  
**Requisitos:** [C-04](../negocio/requisitos.md#c-04), [NFR-09](../negocio/requisitos.md#nfr-09), [NFR-04](../negocio/requisitos.md#nfr-04)

---

## Contexto

A [análise LGPD da Etapa 4](../arquitetura/dados.md#privacidade-e-lgpd) identificou que as tabelas financeiras do sistema não armazenam PII por design. No entanto, o pipeline de observabilidade representa um vetor diferente:

- **Campo `descricao`** pode conter PII inadvertido (`"Venda CPF 123.456.789-00"`) — esse valor aparece nos logs de lançamento registrado
- **Mensagens de erro** podem vazar stack traces com valores de campos de entrada do usuário
- **Eventos do Keycloak** contêm e-mail e ID de usuário dos operadores — pessoas naturais
- **Headers HTTP** logados pelo Traefik ou pelo serviço podem conter tokens ou IPs

O problema é de **persistência inadvertida**: dados sensíveis que nunca deveriam persistir chegam aos logs e ficam retidos por 31 dias no Loki ([política de retenção](../arquitetura/dados.md#política-de-retenção)).

A LGPD (art. 6º, X — responsabilização e prestação de contas) exige que o controlador demonstre medidas técnicas para evitar exposição desnecessária de dados pessoais — inclusive em sistemas de suporte como logging.

---

## Alternativas Consideradas

### Redação na camada de aplicação

Cada serviço é responsável por nunca logar PII — validação no code review, linting customizado.

**Por que foi descartada:**

- Depende de disciplina humana consistente em todo o código — falha inevitável ao longo do tempo
- Não cobre logs de bibliotecas de terceiros (frameworks HTTP, ORMs) que podem logar request bodies
- Não cobre o Keycloak, RabbitMQ, Traefik — componentes fora do controle do código da aplicação

### Redação no momento da consulta (Loki — label filtering)

O Loki não armazena PII, mas permite mascarar na query: `{service="lancamentos"} | replace "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}" "[CPF]"`.

**Por que foi descartada:**

- O dado PII **persiste** no storage do Loki — apenas é mascarado na visualização
- Viola o princípio de minimização (LGPD art. 6º, V): dado que não deveria existir, não deve ser armazenado
- Requer que todos os dashboards e queries usem a máscara — frágil

### Redação no OTEL Collector + Promtail (pipeline de ingestão)

Processamento antes do dado chegar ao backend. PII é substituído por placeholder **antes de persistir** no Loki.

**Por que foi escolhida:**

- Cobertura universal — afeta todos os serviços sem mudar código
- Dado sensível nunca persiste — elimina o risco de vazamento retroativo
- Centralizado e auditável — um único lugar para manter os padrões

---

## Decisão

Aplicar redação de PII em **dois pontos do pipeline**, garantindo cobertura total:

```
Serviços/containers (stdout)
  └── Promtail (replace stage)          ← captura Docker stdout antes do Loki
        └── Loki

Serviços (OTLP)
  └── OTEL Collector (redaction processor)  ← captura logs OTLP antes do Loki
        └── Loki
```

### Padrões de PII detectados

| Dado | Regex | Substituição |
|------|-------|-------------|
| CPF (formatado) | `\d{3}\.\d{3}\.\d{3}-\d{2}` | `[CPF REDACTED]` |
| CPF (somente dígitos) | `(?<!\d)\d{11}(?!\d)` | `[CPF REDACTED]` |
| CNPJ (formatado) | `\d{2}\.\d{3}\.\d{3}/\d{4}-\d{2}` | `[CNPJ REDACTED]` |
| E-mail | `[\w.+-]+@[\w-]+\.[\w.-]+` | `[EMAIL REDACTED]` |
| Cartão de crédito | `\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}` | `[CARD REDACTED]` |
| Telefone BR | `(?:\+55\s?)?\(?\d{2}\)?\s?\d{4,5}[\s-]?\d{4}` | `[PHONE REDACTED]` |

**Estratégia de substituição:** placeholder descritivo (não hash). O hash impede correlação mas também impede auditoria — o operador precisa saber que um CPF estava ali, não recuperar o valor. Para investigações que exijam o dado original, consulta-se a fonte canônica (tabela `lancamentos`).

### O que NÃO é redacted

- `trace_id` e `span_id` — são UUIDs sem PII, críticos para correlação
- `operador_id` — é um ID interno (`usr_7f3b9a10`), não um dado de identificação direta; retido para trilha de auditoria ([NFR-09](../negocio/requisitos.md#nfr-09))
- Campos de negócio como `tipo`, `valor`, `data_competencia` — não são PII

---

## Consequências

### Positivas

- PII nunca persiste no Loki — elimina riscos de vazamento retroativo e conformidade LGPD
- Cobertura universal sem mudança de código nos serviços
- Auditável: o OTEL Collector em modo `summary: debug` conta redações por sessão — alerta se o volume for incomum

### Negativas / Trade-offs

- **Debugging prejudicado:** se um bug envolve um CPF inválido no input, o log mostra `[CPF REDACTED]` — a investigação precisa ir à fonte (banco de dados, com acesso controlado). Trade-off aceito.
- **Falsos positivos:** sequências de 11 dígitos que não são CPF podem ser mascaradas. Regex calibrada para reduzir, mas não eliminar, falsos positivos.
- **Performance:** cada log passa por regex matching — overhead desprezível para o volume esperado (~500 lançamentos/dia).

### Quando revisar

- Se novos campos PII forem introduzidos nos serviços (ex: nome do cliente em RF futuro)
- Se o volume de falsos positivos impactar debugging
- Se regulação exigir hash auditável em vez de placeholder
