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

*[AWS]: Amazon Web Services — provedor de computação em nuvem
*[EKS]: Elastic Kubernetes Service — serviço gerenciado de Kubernetes da AWS
*[RDS]: Relational Database Service — serviço gerenciado de banco de dados relacional da AWS
*[ECR]: Elastic Container Registry — registro de imagens Docker gerenciado pela AWS
*[IAM]: Identity and Access Management — serviço de controle de identidade e acesso da AWS
*[S3]: Simple Storage Service — serviço de armazenamento de objetos da AWS
*[KMS]: Key Management Service — serviço de gerenciamento de chaves criptográficas da AWS
*[WAF]: Web Application Firewall — firewall que filtra e monitora tráfego HTTP malicioso
*[ALB]: Application Load Balancer — balanceador de carga da camada de aplicação (L7) da AWS
*[ACM]: AWS Certificate Manager — serviço de provisionamento e renovação de certificados TLS na AWS
*[VPC]: Virtual Private Cloud — rede virtual isolada na AWS
*[AZ]: Availability Zone — zona de disponibilidade física isolada dentro de uma região AWS
*[AZs]: Availability Zones — zonas de disponibilidade físicas isoladas dentro de uma região AWS
*[CMK]: Customer Managed Key — chave criptográfica gerenciada pelo cliente no AWS KMS
*[SIEM]: Security Information and Event Management — plataforma de coleta, correlação e análise de eventos de segurança
*[SSM]: AWS Systems Manager — serviço de gerenciamento operacional e acesso seguro a instâncias EC2
*[IRSA]: IAM Roles for Service Accounts — mecanismo que associa roles IAM a service accounts Kubernetes, sem credenciais estáticas

*[TLS]: Transport Layer Security — protocolo criptográfico para comunicação segura em rede
*[HTTPS]: HTTP sobre TLS — protocolo HTTP com criptografia via TLS
*[HTTP]: Hypertext Transfer Protocol — protocolo de comunicação para transferência de dados na web
*[AMQP]: Advanced Message Queuing Protocol — protocolo de mensageria para comunicação assíncrona entre sistemas
*[AMQPS]: AMQP Secure — AMQP com criptografia TLS na camada de transporte
*[REST]: Representational State Transfer — estilo arquitetural para APIs sobre HTTP com recursos e verbos padronizados
*[gRPC]: Google Remote Procedure Call — framework de RPC de alta performance que usa HTTP/2 e Protocol Buffers
*[JSON]: JavaScript Object Notation — formato leve de intercâmbio de dados baseado em texto
*[YAML]: YAML Ain't Markup Language — formato de serialização de dados legível por humanos
*[ISO]: International Organization for Standardization — organização que define padrões internacionais
*[TTL]: Time To Live — tempo de vida de um dado em cache ou de um registro DNS antes de expirar
*[CI]: Continuous Integration — prática de integrar código ao repositório principal frequentemente com validação automatizada
*[CD]: Continuous Delivery ou Continuous Deployment — prática de entregar software de forma automatizada e contínua

*[JWKS]: JSON Web Key Set — endpoint que expõe as chaves públicas usadas para verificar assinaturas de JWT
*[PKCE]: Proof Key for Code Exchange — extensão do OAuth2 que protege o fluxo Authorization Code contra interceptação do código de autorização
*[OIDC]: OpenID Connect — camada de identidade sobre OAuth2 que adiciona autenticação e claims de usuário padronizados

*[CNI]: Container Network Interface — especificação e plugins para configurar interfaces de rede em containers
*[SG]: Security Group — firewall virtual stateful que controla tráfego de entrada e saída de recursos AWS
*[SGs]: Security Groups — firewalls virtuais stateful que controlam tráfego de entrada e saída de recursos AWS
*[HPA]: Horizontal Pod Autoscaler — controlador Kubernetes que ajusta o número de réplicas conforme métricas

*[CQRS]: Command Query Responsibility Segregation — padrão que separa operações de escrita (commands) das de leitura (queries)
*[CDC]: Change Data Capture — técnica para capturar alterações em um banco de dados em tempo real, geralmente via WAL
*[WAL]: Write-Ahead Log — log de transações do PostgreSQL usado para recuperação e replicação; base do CDC
*[AOF]: Append-Only File — mecanismo de persistência do Redis que registra cada operação de escrita em um log
*[LRU]: Least Recently Used — política de eviction que descarta os dados acessados há mais tempo quando a memória está cheia
*[ACID]: Atomicity, Consistency, Isolation, Durability — propriedades que garantem confiabilidade em transações de banco de dados
*[ORM]: Object-Relational Mapping — técnica que mapeia objetos de código para tabelas relacionais de banco de dados

*[PII]: Personally Identifiable Information — dado que pode identificar diretamente ou indiretamente uma pessoa natural
*[PDV]: Ponto de Venda — sistema ou terminal utilizado para registrar transações comerciais no ponto de atendimento
*[DLX]: Dead Letter Exchange — exchange do RabbitMQ que recebe mensagens rejeitadas ou expiradas para análise posterior
*[SDD]: Spec-Driven Development — abordagem onde o contrato (OpenAPI/AsyncAPI) é escrito antes do código e dirige a implementação
*[IaC]: Infrastructure as Code — prática de gerenciar e provisionar infraestrutura por meio de arquivos de configuração versionáveis
*[ABB]: Architecture Building Block — bloco arquitetural conceitual e independente de implementação (TOGAF)
*[SBB]: Solution Building Block — implementação concreta de um ABB com tecnologia e produto específicos (TOGAF)
*[TOGAF]: The Open Group Architecture Framework — framework de arquitetura empresarial com metodologia ADM
*[WORM]: Write Once Read Many — política de armazenamento que impede modificação ou exclusão de dados após gravação
*[DSL]: Domain-Specific Language — linguagem especializada para um domínio específico (ex: Structurizr DSL para diagramas C4)

*[BACEN]: Banco Central do Brasil — autoridade monetária brasileira que regula o sistema financeiro nacional
*[RFB]: Receita Federal do Brasil — órgão responsável pela administração tributária federal
