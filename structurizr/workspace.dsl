workspace "Fluxo de Caixa Diário" "Controle de Fluxo de Caixa" {

    model {

        comerciante = person "Comerciante" "Proprietário que precisa controlar o fluxo de caixa diário."

        sistema = softwareSystem "Sistema de Fluxo de Caixa" "Controla lançamentos financeiros e consolida saldos diários." {



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

        comerciante -> lancamentos "Registra lançamentos (débitos e créditos)"
        comerciante -> consolidado "Consulta saldo diário consolidado"
        lancamentos -> broker "Publica evento de lançamento"
        broker -> consolidado "Entrega evento para processamento"

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
