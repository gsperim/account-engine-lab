# Sistema de Fluxo de Caixa Diário

Sistema para controle financeiro de comerciantes, composto por dois serviços independentes:

- **Serviço de Lançamentos** — registra débitos e créditos em tempo real. Nunca pode ficar indisponível.
- **Serviço de Consolidação Diária** — mantém o saldo consolidado por dia, disponível para consulta a qualquer momento.

Os serviços são desacoplados por design via mensageria assíncrona. Uma falha na Consolidação não interrompe o registro de lançamentos.

## Documentação completa

Portal de documentação: **http://localhost:8000**

| Seção | Conteúdo |
|-------|----------|
| Visão Executiva | Jornada do usuário e valor entregue |
| Drivers e Stakeholders | Dores mapeadas e rastreabilidade |
| Principles Catalog | 8 princípios arquiteturais |
| Domínios e Value Stream | Bounded contexts e capacidades |
| Requisitos | RF-01 a RF-09 + NFR-01 a NFR-10 |
| Decisões Arquiteturais | ADRs por fase |
