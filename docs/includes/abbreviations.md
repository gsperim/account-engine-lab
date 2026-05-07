*[NFR]: Requisito Não Funcional
*[RF]: Requisito Funcional
*[ADR]: Architecture Decision Record — registro de uma decisão arquitetural com contexto, decisão e consequências
*[DLQ]: Dead Letter Queue — fila que armazena mensagens que não puderam ser processadas após o número máximo de tentativas
*[UUID]: Universally Unique Identifier — identificador único universal gerado sem coordenação central
*[SLO]: Service Level Objective — meta interna de qualidade de serviço
*[SLA]: Service Level Agreement — acordo contratual de nível de serviço com o cliente
*[JWT]: JSON Web Token — formato compacto e autocontido para transmissão de informações de autenticação
*[mTLS]: Mutual TLS — autenticação mútua via certificados entre cliente e servidor
*[API]: Application Programming Interface — interface que define como componentes se comunicam
*[DDD]: Domain-Driven Design — abordagem de design de software centrada no modelo de domínio
*[C4]: Modelo de diagramas arquiteturais em 4 níveis: Context, Containers, Components e Code
*[LGPD]: Lei Geral de Proteção de Dados (Lei 13.709/2018) — regula o tratamento de dados pessoais no Brasil

*[at-least-once delivery]: Garantia de entrega onde cada mensagem é processada pelo menos uma vez — reentregas são possíveis, por isso o consumidor deve ser idempotente
*[at-least-once]: Garantia de entrega onde cada mensagem é processada pelo menos uma vez — reentregas são possíveis, por isso o consumidor deve ser idempotente
*[retry]: Mecanismo de reenvio automático de uma operação que falhou, geralmente com espera crescente entre tentativas (exponential backoff) para evitar sobrecarga
*[Retry]: Mecanismo de reenvio automático de uma operação que falhou, geralmente com espera crescente entre tentativas (exponential backoff) para evitar sobrecarga
*[exponential backoff]: Estratégia de retry onde o intervalo entre tentativas cresce exponencialmente, reduzindo pressão sobre sistemas sobrecarregados

*[Lançamento]: Registro de uma movimentação financeira com tipo (débito ou crédito), valor, data de competência e descrição
*[lançamento]: Registro de uma movimentação financeira com tipo (débito ou crédito), valor, data de competência e descrição
*[lançamentos]: Registros de movimentações financeiras com tipo (débito ou crédito), valor, data de competência e descrição
*[Lançamentos]: Registros de movimentações financeiras com tipo (débito ou crédito), valor, data de competência e descrição
*[Débito]: Lançamento que representa uma saída de caixa — reduz o saldo
*[débito]: Lançamento que representa uma saída de caixa — reduz o saldo
*[Crédito]: Lançamento que representa uma entrada de caixa — aumenta o saldo
*[crédito]: Lançamento que representa uma entrada de caixa — aumenta o saldo
*[Saldo]: Resultado líquido dos lançamentos em um período: soma dos créditos menos a soma dos débitos
*[saldo]: Resultado líquido dos lançamentos em um período: soma dos créditos menos a soma dos débitos
*[Consolidação Diária]: Saldo calculado considerando todos os lançamentos de uma data de competência específica
*[consolidação diária]: Saldo calculado considerando todos os lançamentos de uma data de competência específica
*[Data de Competência]: Data à qual o lançamento se refere financeiramente, independente da data de registro
*[data de competência]: Data à qual o lançamento se refere financeiramente, independente da data de registro
*[Fluxo de Caixa]: Sequência temporal de lançamentos que representa a movimentação financeira do comerciante
*[fluxo de caixa]: Sequência temporal de lançamentos que representa a movimentação financeira do comerciante
*[Comerciante]: Pessoa física ou jurídica proprietária do negócio que utiliza o sistema para controlar seu caixa
*[comerciante]: Pessoa física ou jurídica proprietária do negócio que utiliza o sistema para controlar seu caixa
