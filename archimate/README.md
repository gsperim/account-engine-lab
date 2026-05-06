# ArchiMate

Modelos ArchiMate do projeto, criados com o [Archi](https://www.archimatetool.com/).

## Estrutura

```
archimate/
├── modelo.archimate     # Arquivo principal do modelo (Archi)
└── views/               # SVGs exportados por view, referenciados na documentação
```

## Views planejadas

| View | Camada | Etapa |
|------|--------|-------|
| Motivation View | Motivation | Etapa 1 |
| Capability Map | Business | Etapa 1 |
| Business Process View | Business | Etapa 1 |
| Application Services View | Application | Etapa 2 |
| Technology View | Technology | Etapa 3 |

## Como exportar

No Archi: `File → Export → Export as Image` → selecionar a view → salvar como SVG em `views/`.
