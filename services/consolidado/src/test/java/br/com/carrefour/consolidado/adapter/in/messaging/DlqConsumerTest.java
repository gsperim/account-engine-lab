package br.com.carrefour.consolidado.adapter.in.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DlqConsumerTest {

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    DlqConsumer consumer;

    @BeforeEach
    void setup() {
        consumer = new DlqConsumer(registry);
    }

    @Test
    void consumir_deveIncrementarMetricaAoReceberMensagem() {
        var message = mock(Message.class);
        when(message.getMessageProperties()).thenReturn(new MessageProperties());

        consumer.consumir(umEvento(), message);

        assertThat(registry.counter("dlq_mensagens_total").count()).isEqualTo(1.0);
    }

    @Test
    void consumir_deveContarTentativasAPartirDoHeaderXDeath() {
        var props = new MessageProperties();
        // x-death ausente → tentativas = 0, não lança exceção
        var message = mock(Message.class);
        when(message.getMessageProperties()).thenReturn(props);

        consumer.consumir(umEvento(), message);

        assertThat(registry.counter("dlq_mensagens_total").count()).isEqualTo(1.0);
    }

    private LancamentoRegistradoEvento umEvento() {
        var payload = new LancamentoRegistradoEvento.Payload(
            UUID.randomUUID(), "CREDITO", new BigDecimal("100.00"),
            LocalDate.of(2026, 5, 9), "operador-1", OffsetDateTime.now());
        return new LancamentoRegistradoEvento(
            UUID.randomUUID(), "LancamentoRegistrado", "1.0", OffsetDateTime.now(), payload);
    }
}
