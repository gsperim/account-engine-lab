package br.com.carrefour.consolidado.adapter.in.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

// Futuro: mensagens retidas na DLQ serão tratadas por módulo de backoffice —
// replay controlado após correção da causa raiz, com audit trail persistido.
@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final Counter dlqTotal;

    public DlqConsumer(MeterRegistry meterRegistry) {
        this.dlqTotal = Counter.builder("dlq_mensagens_total")
                .description("Mensagens que chegaram à DLQ após falha no consumer principal")
                .register(meterRegistry);
    }

    @RabbitListener(queues = RabbitConfig.DLQ)
    public void consumir(LancamentoRegistradoEvento evento, Message amqpMessage) {
        dlqTotal.increment();

        var deaths     = amqpMessage.getMessageProperties().<List<?>>getHeader("x-death");
        var tentativas = deaths != null ? deaths.size() : 0;

        log.atError()
                .addKeyValue("event",                "dlq_mensagem_retida")
                .addKeyValue("lancamento_id",        evento.payload().lancamentoId())
                .addKeyValue("tipo",                 evento.payload().tipo())
                .addKeyValue("tentativas_anteriores", tentativas)
                .log("Mensagem retida na DLQ — requer intervenção manual");
    }
}
