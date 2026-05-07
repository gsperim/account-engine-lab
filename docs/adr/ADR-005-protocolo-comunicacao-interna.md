---
tags:
  - adr
  - decisao
  - integracao
---

# ADR-005 — Protocolo de Comunicação Interna: REST sobre gRPC

**Papéis:** 🔗 Arquiteto de Integração · ⚙️ Arquiteto de Tecnologia
**Data:** 2026-05-07
**Status:** Aceito
**Requisitos:** [NFR-02](../negocio/requisitos.md#nfr-02), [C-01](../negocio/requisitos.md#c-01)

---

## Contexto

Com o API Gateway centralizando o tráfego externo, a comunicação entre gateway e serviços internos (Lançamentos e Consolidação Diária) ocorre em rede privada — sem exposição direta ao exterior. Isso abre a opção de usar um protocolo diferente do REST/JSON para a camada interna, sem impacto no contrato externo.

O candidato natural é **gRPC**: protocolo binário sobre HTTP/2 com contratos fortemente tipados via Protocol Buffers (Protobuf), amplamente adotado em arquiteturas de microserviços de alta performance.

---

## Alternativas Consideradas

| Opção | Vantagens | Desvantagens |
|-------|-----------|--------------|
| **REST/JSON** (escolhida) | Universal, zero toolchain adicional, debuggável com `curl`/Postman, suportado nativamente por qualquer gateway | Payload textual maior; parsing JSON com overhead em escala |
| **gRPC (Protobuf/HTTP2)** | Payload binário compacto, contrato fortemente tipado, HTTP/2 nativo com multiplexing | Requer geração de stubs, toolchain adicional, debugging mais trabalhoso, gateways precisam de transcoding ou suporte explícito |
| **GraphQL interno** | Flexibilidade de queries, útil para clientes com necessidades variadas | Sem benefício para comunicação ponto-a-ponto gateway→serviço; overhead de parsing e introspecção desnecessário |
| **Apache Avro / Thrift** | Binário, schemas versionados | Ecossistema menor, sem vantagem clara sobre Protobuf; não agrega nada que Protobuf não ofereça |

---

## Decisão

Adotar **REST/JSON sobre HTTPS** para a comunicação interna entre API Gateway e serviços.

Justificativas:

- **Volume não justifica otimização prematura** — [NFR-02](../negocio/requisitos.md#nfr-02) define 50 req/s como pico de carga. A diferença de latência entre JSON e Protobuf nessa escala é da ordem de microssegundos — abaixo do ruído de qualquer SLO realista. gRPC entrega ganhos mensuráveis a partir de dezenas de milhares de req/s com payloads volumosos
- **Operação local simplificada** — [C-01](../negocio/requisitos.md#c-01) exige execução via `docker-compose`. REST permite inspecionar, testar e depurar qualquer serviço com `curl` sem dependências adicionais; gRPC exige `grpcurl` ou plugins de IDE e reflexão habilitada no servidor
- **Compatibilidade com gateways** — proxies como Kong, Traefik e NGINX tratam REST nativamente; suporte a gRPC exige configuração adicional (HTTP/2 end-to-end, transcoding ou plugin dedicado)
- **Toolchain enxuto** — gRPC demanda arquivos `.proto`, geração de stubs e sincronização de schemas entre serviços. Para dois serviços, o custo de manutenção supera o benefício
- **Contrato externo inalterado** — o gateway já abstrai o protocolo interno; uma eventual migração para gRPC no futuro não altera a API pública nem o contrato com frontend e PDV

---

## Consequências

### Positivas

- Debugging e desenvolvimento local sem fricção — qualquer membro da equipe inspeciona chamadas internas com ferramentas padrão
- Onboarding simplificado — sem curva de aprendizado de Protobuf e geração de código
- Gateway configurado com rotas REST simples, sem transcoding

### Negativas / Trade-offs

- **Payload maior** — JSON é verboso comparado a Protobuf; aceitável no volume atual
- **Contrato menos rígido** — sem schema enforcement em tempo de compilação; mitigado por testes de contrato (Consumer-Driven Contract Testing) e validação de schema na camada de aplicação
- **Performance não otimizada para escala futura** — se o volume crescer significativamente além de 50 req/s e profiling identificar serialização como gargalo, a migração para gRPC internamente é o próximo passo natural

---

## Caminho de Evolução

A migração para gRPC interno, caso justificada por dados de performance, segue este caminho sem impacto externo:

1. Definir schemas `.proto` para os contratos internos (espelho dos DTOs REST existentes)
2. Configurar os serviços para expor endpoints gRPC em porta separada
3. Atualizar o gateway para rotear internamente via gRPC (ou usar sidecar como Envoy)
4. Remover endpoints REST internos após validação em produção

O contrato externo (REST via gateway) permanece inalterado durante toda a transição.
