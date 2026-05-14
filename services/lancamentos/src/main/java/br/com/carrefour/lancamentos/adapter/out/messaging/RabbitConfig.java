package br.com.carrefour.lancamentos.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE    = "lancamentos.events";
    public static final String ROUTING_KEY = "lancamento.registrado";
    public static final String QUEUE       = "consolidacao.lancamentos";
    public static final String DLQ         = "consolidacao.lancamentos.dlq";
    public static final String DLX         = "dlx";

    // Exchange principal — topic, publicado pelo lancamentos
    @Bean
    TopicExchange lancamentosExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    // Exchange de dead-letter — direct, compartilhado com consolidado
    @Bean
    DirectExchange dlxExchange() {
        return new DirectExchange(DLX, true, false);
    }

    // Fila consumida pelo consolidado — TTL 24h, DLX para reprocessamento
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

    // Binding: lancamentos.events → consolidacao.lancamentos (qualquer routing key lancamento.*)
    @Bean
    Binding consolidacaoBinding(Queue consolidacaoQueue, TopicExchange lancamentosExchange) {
        return BindingBuilder.bind(consolidacaoQueue).to(lancamentosExchange).with("lancamento.#");
    }

    // Binding DLQ: dlx → consolidacao.lancamentos.dlq
    @Bean
    Binding dlqBinding(Queue dlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(dlq).to(dlxExchange).with(DLQ);
    }

    @Bean
    MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        return template;
    }
}
