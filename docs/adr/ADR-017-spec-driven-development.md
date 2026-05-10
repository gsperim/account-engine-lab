---
tags:
  - adr
  - decisao
  - engenharia
  - contratos
---

# ADR-017 — Metodologia de Implementação: Spec-Driven Development

**Papéis:** 🛠️ Engenheiro de Software · 🔗 Arquiteto de Integração · 🧩 Arquiteto de Soluções  
**Data:** 2026-05-09  
**Status:** Aceito  
**Requisitos:** [RF-01](../negocio/requisitos.md#rf-01), [RF-04](../negocio/requisitos.md#rf-04) · [Contratos de Integração](../engenharia/contratos.md)

---

## Contexto

A Fase 2 definiu os contratos de integração em Markdown — estrutura de eventos e endpoints REST. A Etapa 7 converte esses contratos em código. A questão é: **qual é a fonte da verdade — o contrato ou o código?**

Duas abordagens são possíveis:

### Code-first (springdoc-openapi)

O desenvolvedor escreve o controller com anotações (`@Operation`, `@ApiResponse`) e a ferramenta gera o `openapi.yaml` a partir do código.

**Problema:** o contrato é uma consequência do código, não uma obrigação sobre ele. Se o controller divergir do que foi acordado, ninguém sabe até o consumidor quebrar em produção.

### Spec-first (OpenAPI Generator)

O `openapi.yaml` é escrito antes do código. A ferramenta gera interfaces Spring a partir do YAML. O compilador recusa código que não implemente todas as operações do contrato.

**Vantagem:** a divergência entre spec e implementação é um erro de compilação, não um bug em produção.

---

## Decisão

Adotar **Spec-Driven Development (SDD)** com abordagem **spec-first** usando OpenAPI Generator no padrão de interface direta:

```
contracts/openapi/lancamentos.yaml   ← fonte da verdade
    ↓  openapi-generator-maven-plugin (generate-sources)
services/lancamentos/target/generated-sources/
    └── api/LancamentosApi.java      ← interface gerada, nunca editada
         ↓  implementada por
adapter/in/rest/LancamentoController.java   ← seu código
```

O plugin roda em `generate-sources` — antes da compilação. Se o controller não implementar todas as operações do YAML, o build falha.

### Padrão escolhido: interface direta (não delegate)

O gerador cria uma interface Java com anotações Spring MVC. O controller implementa essa interface. É o padrão mais legível para o avaliador — uma classe, sem indireção extra.

```java
// Gerado a partir do openapi.yaml — nunca editar
@RequestMapping("/lancamentos")
public interface LancamentosApi {
    @PostMapping
    ResponseEntity<LancamentoResponse> registrar(@Valid @RequestBody LancamentoRequest body);

    @GetMapping
    ResponseEntity<List<LancamentoResponse>> listar(@RequestParam LocalDate data);
}

// Escrito pelo desenvolvedor — implementa o contrato
@RestController
@RequiredArgsConstructor
public class LancamentoController implements LancamentosApi {

    private final RegistrarLancamentoUseCase registrar;

    @Override
    public ResponseEntity<LancamentoResponse> registrar(LancamentoRequest body) {
        // chama o use case, retorna DTO
    }
}
```

### Onde vivem os contratos

```
contracts/
├── openapi/
│   ├── lancamentos.yaml    ← API REST do Serviço de Lançamentos
│   └── consolidado.yaml    ← API REST do Serviço de Consolidação
└── asyncapi/
    └── eventos.yaml        ← Eventos de domínio via RabbitMQ
```

Os YAMLs em `contracts/` são a especificação. O código em `services/` é a implementação. A spec existe independentemente do código — pode ser lida, versionada e compartilhada com consumidores sem clonar os serviços.

---

## Alternativas Consideradas

| Alternativa | Por que descartada |
|-------------|-------------------|
| Code-first com springdoc | Contrato é derivado do código — não garante conformidade, inverte a hierarquia |
| Delegate pattern | Adiciona uma classe extra (`ApiDelegate`) sem benefício claro para dois serviços — aumenta indireção sem reduzir acoplamento |
| Sem geração (spec como referência) | Nada garante que implementação bate com spec — derrota o propósito |
| gRPC + Protobuf | Adequado para comunicação interna entre serviços; REST é o contrato público — ADR-005 mantém REST |

---

## Consequências

### Positivas

- **Conformidade garantida em compile-time** — divergência entre spec e código é erro de build
- **Spec compartilhável** — `contracts/openapi/lancamentos.yaml` pode ser publicado no portal de APIs sem depender do código
- **Onboarding mais rápido** — novo desenvolvedor lê o YAML e entende o contrato antes de abrir o código
- **Testes de contrato automáticos** — ferramentas como Pact ou Spring Cloud Contract podem validar o consumidor contra o mesmo YAML

### Negativas / Trade-offs

- **Geração no build** — `mvn generate-sources` precisa rodar antes de qualquer IDE reconhecer as interfaces; requer configuração do IDE para marcar `target/generated-sources` como source root
- **YAML antes do código** — o desenvolvedor precisa pensar no contrato antes de implementar; é uma restrição intencional, mas exige disciplina
- **Evolução do contrato requer regeneração** — mudança no YAML requer `mvn generate-sources` para o IDE refletir as mudanças

### Quando revisar

- Se os serviços precisarem de contratos gRPC para comunicação interna (avaliar Protobuf + grpc-gateway)
- Se o número de serviços crescer e justificar um API registry centralizado (Apicurio, AWS API Gateway)
