package br.com.carrefour.consolidado.adapter.in.messaging;

import br.com.carrefour.consolidado.domain.model.TipoMovimento;
import br.com.carrefour.consolidado.domain.port.in.ProcessarLancamentoUseCase;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LancamentoEventoConsumer {

    private static final Logger log = LoggerFactory.getLogger(LancamentoEventoConsumer.class);

    private final ProcessarLancamentoUseCase processarUseCase;

    public LancamentoEventoConsumer(ProcessarLancamentoUseCase processarUseCase) {
        this.processarUseCase = processarUseCase;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    @CircuitBreaker(name = "rabbit-consumer", fallbackMethod = "consumirFallback")
    public void consumir(LancamentoRegistradoEvento evento, Message amqpMessage) {
        var payload = evento.payload();

        log.atInfo()
                .addKeyValue("event",         "evento_recebido")
                .addKeyValue("lancamento_id", payload.lancamentoId())
                .addKeyValue("tipo",          payload.tipo())
                .log("Evento de lançamento recebido");

        processarUseCase.executar(new ProcessarLancamentoUseCase.Command(
                payload.lancamentoId(),
                TipoMovimento.de(payload.tipo()),
                payload.valor(),
                payload.dataCompetencia()
        ));

        log.atInfo()
                .addKeyValue("event",         "saldo_atualizado")
                .addKeyValue("lancamento_id", payload.lancamentoId())
                .addKeyValue("data",          payload.dataCompetencia())
                .log("Saldo consolidado atualizado");
    }

    @SuppressWarnings("unused")
    private void consumirFallback(LancamentoRegistradoEvento evento, Message amqpMessage, Throwable t) {
        log.atError()
                .addKeyValue("event",         "consumer_circuit_aberto")
                .addKeyValue("lancamento_id", evento.payload().lancamentoId())
                .addKeyValue("motivo",        t.getMessage())
                .setCause(t)
                .log("Circuit breaker aberto — mensagem encaminhada para DLQ");
        throw new RuntimeException("circuit breaker aberto: " + t.getMessage(), t);
    }
}
