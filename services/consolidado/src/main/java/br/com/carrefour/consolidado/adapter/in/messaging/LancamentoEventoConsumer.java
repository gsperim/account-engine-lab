package br.com.carrefour.consolidado.adapter.in.messaging;

import br.com.carrefour.consolidado.domain.model.TipoMovimento;
import br.com.carrefour.consolidado.domain.port.in.ProcessarLancamentoUseCase;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class LancamentoEventoConsumer {

    private static final Logger log = LoggerFactory.getLogger(LancamentoEventoConsumer.class);
    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    private final ProcessarLancamentoUseCase processarUseCase;

    public LancamentoEventoConsumer(ProcessarLancamentoUseCase processarUseCase) {
        this.processarUseCase = processarUseCase;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    @CircuitBreaker(name = "rabbit-consumer", fallbackMethod = "consumirFallback")
    public void consumir(LancamentoRegistradoEvento evento, Message amqpMessage) {
        var correlationId = Optional.ofNullable(
                amqpMessage.getMessageProperties().<String>getHeader(CORRELATION_HEADER))
                .orElseGet(() -> UUID.randomUUID().toString());

        MDC.put("correlation_id", correlationId);
        try {
            var payload = evento.payload();
            log.info("evento recebido lancamento_id={} tipo={} valor={}",
                    payload.lancamentoId(), payload.tipo(), payload.valor());

            processarUseCase.executar(new ProcessarLancamentoUseCase.Command(
                    payload.lancamentoId(),
                    TipoMovimento.de(payload.tipo()),
                    payload.valor(),
                    payload.dataCompetencia()
            ));

            log.info("saldo atualizado lancamento_id={} data={}", payload.lancamentoId(), payload.dataCompetencia());
        } finally {
            MDC.remove("correlation_id");
        }
    }

    // Fallback: circuit aberto — rejeita a mensagem para a DLQ em vez de bloquear o listener.
    private void consumirFallback(LancamentoRegistradoEvento evento, Message amqpMessage, Throwable t) {
        log.error("consumer_circuit_aberto lancamento_id={} motivo={} — mensagem encaminhada para DLQ",
                evento.payload().lancamentoId(), t.getMessage());
        throw new RuntimeException("circuit breaker aberto: " + t.getMessage(), t);
    }
}
