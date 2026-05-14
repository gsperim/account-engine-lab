package br.com.carrefour.consolidado.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String QUEUE = "consolidacao.lancamentos";
    public static final String DLQ   = "consolidacao.lancamentos.dlq";
    public static final String DLX   = "dlx";

    // Exchange de dead-letter — idempotente, também declarado em lancamentos
    @Bean
    DirectExchange dlxExchange() {
        return new DirectExchange(DLX, true, false);
    }

    // Fila principal — argumentos idênticos aos do definitions.json e lancamentos/RabbitConfig
    @Bean
    Queue consolidacaoQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange",    DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .withArgument("x-message-ttl",             86400000L)
                .build();
    }

    @Bean
    Queue dlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding dlqBinding(Queue dlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(dlq).to(dlxExchange).with(DLQ);
    }

    @Bean
    MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
