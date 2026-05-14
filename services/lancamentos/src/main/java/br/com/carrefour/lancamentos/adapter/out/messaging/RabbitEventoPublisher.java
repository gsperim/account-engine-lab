package br.com.carrefour.lancamentos.adapter.out.messaging;

import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.port.out.EventoPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.UUID;

// Publicação direta usada apenas por testes — em produção o OutboxRelay chama o RabbitTemplate.
@Component
public class RabbitEventoPublisher implements EventoPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitEventoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publicarLancamentoRegistrado(Lancamento lancamento) {
        var evento = new LancamentoRegistradoEvento(
                UUID.randomUUID(),
                "LancamentoRegistrado",
                "1.0",
                lancamento.getCriadoEm().atOffset(ZoneOffset.UTC),
                new LancamentoRegistradoEvento.Payload(
                        lancamento.getId().toUUID(),
                        lancamento.getTipo().name(),
                        lancamento.getValor().toBigDecimal(),
                        lancamento.getDataCompetencia(),
                        lancamento.getOperadorId(),
                        lancamento.getCriadoEm().atOffset(ZoneOffset.UTC)
                )
        );
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, evento);
    }
}
