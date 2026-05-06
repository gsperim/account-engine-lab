# ADR-003 — ArchiMate 3.x como Notação de Arquitetura de Negócio e Solução

**Chapéus:** 🏛️ Arquiteto Corporativo · 💼 Arquiteto de Negócios · 🧩 Arquiteto de Soluções
**Status:** Aceito
**Data:** 2026-05-06

---

## Contexto

O desafio exige documentação em múltiplas camadas arquiteturais: motivação e estratégia, capacidades de negócio, processos, serviços de aplicação e infraestrutura. É necessária uma notação que cubra essas camadas com consistência, rastreabilidade entre elementos e legibilidade para stakeholders não-técnicos.

O TOGAF ADM — referência metodológica parcialmente adotada neste projeto (ver roteiro) — usa o ArchiMate como linguagem de modelagem de referência para as fases A→D.

**Driver:** D00 — necessidade de documentação arquitetural multi-camada com rastreabilidade entre negócio e tecnologia.

---

## Decisão

Adotar **ArchiMate 3.x** como notação para as camadas de negócio e solução, modelado na ferramenta **Archi** (open-source).

### Camadas e views utilizadas

| Camada ArchiMate | Views planejadas |
|-----------------|-----------------|
| **Motivation** | Drivers, goals, principles catalog, stakeholder map |
| **Business Layer** | Capability map, business processes, actors e roles |
| **Application Layer** | Application services, application components, interfaces |
| **Technology Layer** | Deployment nodes, infraestrutura, topologia de rede |

### Relacionamentos-chave a explorar

- `Serving` — serviços de aplicação que suportam processos de negócio
- `Realization` — componentes que realizam serviços
- `Triggering` / `Flow` — sequência e fluxo de informação entre processos
- `Assignment` — atribuição de atores a processos e funções
- `Composition` / `Aggregation` — decomposição de capacidades e serviços

### Ponto de transição para C4

A Camada de Aplicação do ArchiMate corresponde ao nível Container (L2) do C4. Elementos definidos como *Application Component* no ArchiMate são refinados como *Containers* no Structurizr DSL. Essa correspondência deve ser mantida explícita nos dois modelos para garantir consistência.

---

## Alternativas Consideradas

| Alternativa | Motivo do Descarte |
|-------------|-------------------|
| UML (casos de uso, componentes, deployment) | Não cobre a camada de negócio e motivação; sem semântica de capacidades |
| BPMN | Excelente para processos, mas não cobre arquitetura de solução nem tecnologia |
| C4 em todas as camadas | Não tem representação de capacidades de negócio, atores organizacionais ou motivação |
| Notação proprietária / draw.io livre | Sem semântica formal; dificulta rastreabilidade entre elementos |
| Flowchart / diagrams ad-hoc | Sem consistência ou reutilização de elementos entre views |

---

## Consequências

**Positivas:**
- Notação de referência do mercado para arquitetura corporativa e de soluções
- Modelo consistente — elementos definidos uma vez, reutilizados em múltiplas views sem inconsistência
- Rastreabilidade natural entre camadas: driver de negócio → capability → application service → componente técnico
- Alinhado com o TOGAF ADM parcialmente adotado
- Archi é open-source, sem dependência de licença

**Negativas / Trade-offs:**
- Curva de aprendizado da notação para quem não conhece ArchiMate
- Arquivo `.archimate` é binário/XML — diffs no Git não são legíveis sem a ferramenta
- Exportação de SVGs é manual — não há geração automática no pipeline CI
- Stakeholders técnicos (desenvolvedores) tendem a preferir C4 para a camada de aplicação — daí a transição para Structurizr nas camadas mais baixas

---

## Referências

- [ArchiMate 3.2 Specification — The Open Group](https://pubs.opengroup.org/architecture/archimate3-doc/)
- [Archi — ArchiMate Modelling Tool](https://www.archimatetool.com/)
- ADR-001 — Ferramentas de Documentação Arquitetural
