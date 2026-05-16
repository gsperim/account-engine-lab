package br.com.carrefour.consolidado.adapter.in.messaging;

import br.com.carrefour.consolidado.domain.model.TipoMovimento;
import br.com.carrefour.consolidado.domain.port.in.ProcessarLancamentoUseCase;
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

@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);
    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    private final ProcessarLancamentoUseCase processarUseCase;
    private final Counter dlqTotal;

    public DlqConsumer(ProcessarLancamentoUseCase processarUseCase, MeterRegistry meterRegistry) {
        this.processarUseCase = processarUseCase;
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
            log.error("dlq_mensagem_recebida lancamento_id={} tentativas_anteriores={} tipo={}",
                    evento.payload().lancamentoId(), tentativas, evento.payload().tipo());

            processarUseCase.executar(new ProcessarLancamentoUseCase.Command(
                    evento.payload().lancamentoId(),
                    TipoMovimento.de(evento.payload().tipo()),
                    evento.payload().valor(),
                    evento.payload().dataCompetencia()
            ));

            log.info("dlq_reprocessado lancamento_id={}", evento.payload().lancamentoId());
        } catch (Exception e) {
            log.error("dlq_reprocessamento_falhou lancamento_id={} motivo={} — mensagem descartada",
                    evento.payload().lancamentoId(), e.getMessage());
        } finally {
            MDC.remove("correlation_id");
        }
    }
}
