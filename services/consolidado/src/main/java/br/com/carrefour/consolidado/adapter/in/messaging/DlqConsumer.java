package br.com.carrefour.consolidado.adapter.in.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Futuro: mensagens retidas na DLQ serão tratadas por módulo de backoffice —
// replay controlado após correção da causa raiz, com audit trail persistido.
@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);
    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    private final Counter dlqTotal;

    public DlqConsumer(MeterRegistry meterRegistry) {
        this.dlqTotal = Counter.builder("dlq_mensagens_total")
                .description("Mensagens que chegaram à DLQ após falha no consumer principal")
                .register(meterRegistry);
    }

    @RabbitListener(queues = RabbitConfig.DLQ)
    public void consumir(LancamentoRegistradoEvento evento, Message amqpMessage) {
        dlqTotal.increment();

        var props = amqpMessage.getMessageProperties();
        var correlationId = Optional.ofNullable(props.<String>getHeader(CORRELATION_HEADER))
                .orElseGet(() -> UUID.randomUUID().toString());
        var deaths = props.<List<?>>getHeader("x-death");
        var tentativas = deaths != null ? deaths.size() : 0;

        MDC.put("correlation_id", correlationId);
        try {
            log.error("dlq_mensagem_retida lancamento_id={} tentativas_anteriores={} tipo={} — requer intervenção manual",
                    evento.payload().lancamentoId(), tentativas, evento.payload().tipo());
        } finally {
            MDC.remove("correlation_id");
        }
    }
}
