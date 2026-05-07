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
| **Evento de Lançamento** | Mensagem publicada pelo Contexto de Lançamentos ao registrar um novo lançamento, consumida pela Consolidação | Integração |
| **Comerciante** | Pessoa física ou jurídica proprietária do negócio que utiliza o sistema para controlar seu caixa | Negócio |
