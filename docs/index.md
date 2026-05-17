# Fluxo de Caixa Diário

Portal de documentação do desafio técnico para a vaga de **Arquiteto de Soluções**.

---

## Sobre o Projeto

Um comerciante precisa controlar seu fluxo de caixa diário com lançamentos (débitos e créditos) e visualizar o saldo diário consolidado.

**Dois serviços principais:**

| Serviço | Responsabilidade |
|---------|-----------------|
| Lançamentos | Registro de débitos e créditos em tempo real |
| Consolidação Diária | Saldo consolidado por dia com suporte a 50 req/s |

**NFR crítico:** o Serviço de Lançamentos não pode ficar indisponível se a Consolidação Diária cair.

---

## Navegação Rápida

- [Decisões arquiteturais (ADRs)](adr/index.md)
- [Guias de Contribuição](../CONTRIBUTING.md)

---

## Documentação Publicada

| Artefato | URL |
|----------|-----|
| Documentação (MkDocs) | [gsperim.github.io/account-engine-lab](https://gsperim.github.io/account-engine-lab/) |
| Diagramas C4 (Structurizr) | [.../c4/](https://gsperim.github.io/account-engine-lab/c4/) |
| Relatórios de testes — Lançamentos | [.../tests/lancamentos/](https://gsperim.github.io/account-engine-lab/tests/lancamentos/) |
| Relatórios de testes — Consolidado | [.../tests/consolidado/](https://gsperim.github.io/account-engine-lab/tests/consolidado/) |
| Estimativa de custo (Infracost) | [.../infracost/](https://gsperim.github.io/account-engine-lab/infracost/) |

---

## Como Executar Localmente

```bash
docker-compose up
```

| Artefato | URL |
|----------|-----|
| Documentação (MkDocs) | [http://localhost:8000](http://localhost:8000) |
| Diagramas C4 (Structurizr Lite) | [http://localhost:8080](http://localhost:8080) |
