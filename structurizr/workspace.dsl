workspace "Fluxo de Caixa Diário" "Controle de Fluxo de Caixa" {

    model {

        caixa = person "Caixa" "Operador que registra débitos e créditos no momento da transação."
        gestor = person "Gestor" "Responsável por acompanhar o saldo consolidado e tomar decisões financeiras."

        authService = softwareSystem "Serviço de Autenticação" "Emite e valida tokens de acesso. Decisão de implementação (Keycloak, Auth0, JWT próprio) definida na Etapa 5." {
            tags "External"
        }

        sistema = softwareSystem "Sistema de Fluxo de Caixa" "Controla lançamentos financeiros e consolida saldos diários." {
            !docs overview.md



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

        caixa -> authService "Autentica"
        gestor -> authService "Autentica"
        caixa -> lancamentos "Registra débitos e créditos"
        gestor -> consolidado "Consulta saldo diário consolidado"
        lancamentos -> broker "Publica evento de lançamento"
        broker -> consolidado "Entrega evento para processamento"
        sistema -> authService "Valida tokens de acesso" "JWT/HTTPS"

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

        themes theme.json

    }

}
