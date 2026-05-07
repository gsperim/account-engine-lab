# Sistema de Fluxo de Caixa Diário

Sistema para controle financeiro de comerciantes, composto por dois serviços independentes:

- **Serviço de Lançamentos** — registra débitos e créditos em tempo real. Nunca pode ficar indisponível.
- **Serviço de Consolidação Diária** — mantém o saldo consolidado por dia, disponível para consulta a qualquer momento.

Os serviços são desacoplados por design via mensageria assíncrona. Uma falha na Consolidação não interrompe o registro de lançamentos.

## Documentação completa

Portal: [http://localhost:8000](http://localhost:8000)

| Seção | Link |
|-------|------|
| Visão Executiva | [http://localhost:8000/negocio/visao-executiva/](http://localhost:8000/negocio/visao-executiva/) |
| Drivers e Stakeholders | [http://localhost:8000/negocio/drivers/](http://localhost:8000/negocio/drivers/) |
| Principles Catalog | [http://localhost:8000/negocio/principios/](http://localhost:8000/negocio/principios/) |
| Domínios e Value Stream | [http://localhost:8000/negocio/dominios/](http://localhost:8000/negocio/dominios/) |
| Requisitos | [http://localhost:8000/negocio/requisitos/](http://localhost:8000/negocio/requisitos/) |
| Decisões Arquiteturais | [http://localhost:8000/adr/](http://localhost:8000/adr/) |
| Glossário | [http://localhost:8000/glossario/](http://localhost:8000/glossario/) |
