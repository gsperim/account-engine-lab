---
tags:
  - negocio
  - ddd
---

# Glossário — Ubiquitous Language

**Papel:** 💼 Arquiteto de Negócios
**Framework:** DDD — Ubiquitous Language

Termos do domínio utilizados de forma consistente em todo o projeto: código, documentação, ADRs e conversas com stakeholders.

---

| Termo | Definição | Contexto |
|-------|-----------|---------|
| **Lançamento** | Registro de uma movimentação financeira com tipo, valor, data e descrição | Lançamentos |
| **Débito** | Lançamento que representa uma saída de caixa — reduz o saldo | Lançamentos |
| **Crédito** | Lançamento que representa uma entrada de caixa — aumenta o saldo | Lançamentos |
| **Valor** | Montante monetário de um lançamento, sempre positivo; o tipo (débito/crédito) define o sinal | Lançamentos |
| **Data de Competência** | Data à qual o lançamento se refere financeiramente, independente da data de registro | Lançamentos |
| **Saldo** | Resultado líquido dos lançamentos em um período: soma dos créditos menos a soma dos débitos | Consolidação |
| **Consolidação Diária** | Saldo calculado considerando todos os lançamentos de uma data de competência específica | Consolidação |
| **Fluxo de Caixa** | Sequência temporal de lançamentos que representa a movimentação financeira do comerciante | Ambos |
| **`LancamentoRegistrado`** | Evento de domínio publicado pelo Serviço de Lançamentos após persistência bem-sucedida, consumido pelo Serviço de Consolidação Diária | Integração |
| **Comerciante** | Pessoa física ou jurídica proprietária do negócio que utiliza o sistema para controlar seu caixa | Negócio |
| **Caixa** | Operador responsável pelo registro das movimentações financeiras no dia a dia | Negócio |
| **Gestor** | Responsável pela análise do fluxo de caixa e tomada de decisão financeira | Negócio |
| **Estorno** | Lançamento compensatório que reverte financeiramente um lançamento anterior, criando um vínculo rastreável via campo `estorno_de` | Lançamentos |
| **`TotaisDiarioCalculado`** | Evento de domínio publicado pelo Serviço de Lançamentos em resposta a um pedido de recálculo assíncrono ([RF-07](negocio/requisitos.md#rf-07)), contendo a soma de créditos e débitos de um dia específico | Integração |
| **`LancamentoEstornado`** | Evento de domínio publicado pelo Serviço de Lançamentos após a confirmação de um estorno ([RF-08](negocio/requisitos.md#rf-08)), contendo o vínculo entre o estorno e o lançamento original | Integração |
