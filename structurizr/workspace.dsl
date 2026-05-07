workspace "Fluxo de Caixa Diário" "Controle de Fluxo de Caixa" {

    model {

        caixa = person "Caixa" "Operador que registra débitos e créditos no momento da transação."
        gestor = person "Gestor" "Responsável por acompanhar o saldo consolidado e tomar decisões financeiras."

        authService = softwareSystem "Serviço de Autenticação" "Emite e valida tokens de acesso. Decisão de implementação (Keycloak, Auth0, JWT próprio) definida na Etapa 5." {
            tags "External"
        }

        pdv = softwareSystem "Sistema PDV" "Ponto de venda externo que envia lançamentos diretamente via API — canal alternativo ao frontend para operações integradas." {
            tags "External"
        }

        sistema = softwareSystem "Sistema de Fluxo de Caixa" "Controla lançamentos financeiros e consolida saldos diários." {
            !docs overview.md

            frontend = container "Aplicação Web" {
                description "Interface para registro de lançamentos pelo Caixa e consulta de saldo consolidado pelo Gestor."
                tags "Web"
            }

            lancamentos = container "Serviço de Lançamentos" {
                description "Registra débitos e créditos. Não pode ficar indisponível se a Consolidação cair."
                tags "Service"
            }

            consolidado = container "Serviço de Consolidação Diária" {
                description "Calcula e disponibiliza o saldo consolidado por dia. Suporta 50 req/s com até 5% de perda."
                tags "Service"
            }

            broker = container "Message Broker" {
                description "Garante desacoplamento assíncrono entre Lançamentos e Consolidação."
                tags "Broker"
            }

        }

        caixa -> authService "Autentica" "OAuth2 Authorization Code"
        gestor -> authService "Autentica" "OAuth2 Authorization Code"
        pdv -> authService "Autentica" "OAuth2 Client Credentials"
        caixa -> frontend "Registra lançamentos" "HTTPS"
        gestor -> frontend "Consulta saldo consolidado" "HTTPS"
        frontend -> authService "Redireciona para login" "OAuth2 Authorization Code"
        frontend -> lancamentos "Envia lançamentos" "REST/HTTPS"
        frontend -> consolidado "Consulta saldo" "REST/HTTPS"
        pdv -> lancamentos "Envia lançamentos via API" "REST/HTTPS · OAuth2 Client Credentials"
        lancamentos -> authService "Obtém chaves públicas (JWKS)" "HTTPS · cache local"
        lancamentos -> broker "Publica evento de lançamento"
        broker -> consolidado "Entrega evento para processamento"
        consolidado -> authService "Obtém chaves públicas (JWKS)" "HTTPS · cache local"

    }

    views {

        systemContext sistema "contexto" {
            title "Contexto do Sistema — Fluxo de Caixa Diário"
            include *
            autoLayout
        }

        container sistema "containers" {
            title "Containers — Fluxo de Caixa Diário"
            include *
            autoLayout
        }

        styles {
            element "External" {
                background "#999999"
                color "#ffffff"
                opacity 50
            }
        }

        themes theme.json

    }

}
