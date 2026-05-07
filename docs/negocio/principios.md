# Principles Catalog

**Papel:** 🏛️ Arquiteto Corporativo · 🧩 Arquiteto de Soluções
**Framework:** ArchiMate — Motivation View (Principle)

Princípios arquiteturais que guiam todas as decisões do projeto. Cada decisão de design, ADR ou escolha tecnológica deve ser justificada à luz destes princípios.

---

| ID | Princípio | Declaração | Origem | Implicações |
|----|-----------|------------|--------|-------------|
| <span id="p-01"></span>P-01 | **Desacoplamento por Design** | Serviços não devem ter dependência síncrona direta entre si | [NFR-01](requisitos.md#nfr-01) | Comunicação assíncrona via mensageria; falha em um serviço não propaga para outro |
| <span id="p-02"></span>P-02 | **Resiliência Primeiro** | O registro de lançamentos deve funcionar mesmo que subsistemas dependentes falhem | [NFR-01](requisitos.md#nfr-01), [D-03](drivers.md#d-03) | Circuit breaker, retry com backoff, filas duráveis |
| <span id="p-03"></span>P-03 | **Consistência Eventual entre Serviços** | Consistência forte dentro de cada serviço; consistência eventual entre serviços é aceitável | [NFR-01](requisitos.md#nfr-01), [P-01](#p-01) | A consolidação pode estar momentaneamente desatualizada; lançamentos nunca são perdidos |
| <span id="p-04"></span>P-04 | **Database per Service** | Cada serviço possui e controla exclusivamente seus próprios dados | [P-01](#p-01) | Sem acesso direto ao banco de outro serviço; integração apenas via eventos ou APIs |
| <span id="p-05"></span>P-05 | **Contratos Explícitos** | Toda comunicação entre serviços ocorre via contratos formais e versionados | [P-01](#p-01), [D-03](drivers.md#d-03) | Schemas de eventos documentados; versionamento de APIs e mensagens |
| <span id="p-06"></span>P-06 | **Observabilidade Nativa** | Logs estruturados, métricas e rastreamento distribuído são requisitos, não opcionais | [NFR-02](requisitos.md#nfr-02), [D-04](drivers.md#d-04) | Instrumentação desde o início; SLOs definidos antes do go-live |
| <span id="p-07"></span>P-07 | **Escalabilidade Horizontal** | Serviços devem escalar adicionando instâncias, não aumentando recursos de uma instância | [NFR-02](requisitos.md#nfr-02) | Stateless por design; estado externalizado em banco e cache |
| <span id="p-08"></span>P-08 | **Segurança por Design** | Autenticação, autorização e proteção de dados são considerados desde a concepção | Requisito diferencial | Zero trust entre serviços; mTLS ou JWT para comunicação interna |
