package br.com.carrefour.lancamentos.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Bean separado para que @CircuitBreaker e @Retry sejam aplicados via Spring AOP proxy.
// Self-invocation (processar() → publicar() no mesmo bean) bypassa o proxy — por isso a extração.
@Component
public class OutboxPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper   mapper;

    public OutboxPublisher(RabbitTemplate rabbitTemplate, ObjectMapper mapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.mapper         = mapper;
    }

    @CircuitBreaker(name = "rabbit-publisher")
    @Retry(name = "rabbit-publisher")
    public void publicar(String payload, UUID lancamentoId) throws Exception {
        var evento = mapper.readValue(payload, LancamentoRegistradoEvento.class);
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_KEY,
                evento,
                msg -> {
                    msg.getMessageProperties().setHeader("X-Outbox-ID", lancamentoId.toString());
                    return msg;
                }
        );
    }
}
