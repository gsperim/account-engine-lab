# Diagramas C4

**Perspectiva:** 🧩 Arquiteto de Soluções  
**Nível:** C4 L1 (System Context) + C4 L2 (Containers)  
**Fonte:** [`structurizr/workspace.dsl`](../../structurizr/workspace.dsl)

---

## Visualização Interativa

Os diagramas são renderizados pelo Structurizr Lite incluído no ambiente local:

```bash
docker compose up structurizr
# Acesse: http://localhost:8080
```

---

## C4 L1 — Contexto do Sistema

> Exportação em PNG disponível após execução do Structurizr. Execute `docker compose up structurizr` e exporte pelo menu *Diagrams → Export*.

<!-- TODO Etapa 9: substituir pelo PNG exportado
![Diagrama de Contexto do Sistema](../assets/c4-context.png)
-->

**O que mostra:** o sistema como uma caixa preta, seus usuários diretos (Comerciante, PDV) e os sistemas externos com os quais se integra.

---

## C4 L2 — Diagrama de Containers

> Exportação em PNG disponível após execução do Structurizr.

<!-- TODO Etapa 9: substituir pelo PNG exportado
![Diagrama de Containers](../assets/c4-containers.png)
-->

**O que mostra:** os containers internos do sistema — Serviço de Lançamentos, Serviço de Consolidação Diária, bancos de dados, cache e broker — e como se comunicam entre si e com o mundo externo.

---

## Decisões de Design

Os diagramas foram produzidos em [Structurizr DSL](https://structurizr.com/dsl) e renderizados pelo Structurizr Lite. Esta escolha elimina dependência de ferramentas externas — qualquer pessoa com Docker consegue visualizar os diagramas sem conta ou licença.

A decisão de runtime está formalizada no [ADR-006](../adr/ADR-006-container-runtime.md).
