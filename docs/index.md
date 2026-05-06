# Fluxo de Caixa Diário — Banco Carrefour

Portal de documentação do desafio técnico para a vaga de **Arquiteto de Soluções**.

---

## Sobre o Projeto

Um comerciante precisa controlar seu fluxo de caixa diário com lançamentos (débitos e créditos) e visualizar o saldo diário consolidado.

**Dois serviços principais:**

| Serviço | Responsabilidade |
|---------|-----------------|
| Lançamentos | Registro de débitos e créditos em tempo real |
| Consolidado Diário | Saldo consolidado por dia com suporte a 50 req/s |

**NFR crítico:** o Serviço de Lançamentos não pode ficar indisponível se o Consolidado Diário cair.

---

## Navegação Rápida

- [Enunciado do desafio](../base-de-conhecimento/desafio-arquiteto-solucoes.md)
- [Roteiro de execução](../base-de-conhecimento/roteiro.md)
- [Decisões arquiteturais (ADRs)](adr/index.md)

---

## Como Executar Localmente

```bash
docker-compose up
```

| Serviço | URL |
|---------|-----|
| Documentação (MkDocs) | [http://localhost:8000](http://localhost:8000) |
| Diagramas C4 (Structurizr Lite) | [http://localhost:8080](http://localhost:8080) |
