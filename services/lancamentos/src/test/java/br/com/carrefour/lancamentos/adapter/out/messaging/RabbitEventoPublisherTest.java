package br.com.carrefour.lancamentos.adapter.out.messaging;

import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.model.Valor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitEventoPublisherTest {

    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks RabbitEventoPublisher publisher;

    static final UUID LANCAMENTO_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void devePublicarNoExchangeCorretoComRoutingKey() {
        publisher.publicarLancamentoRegistrado(umLancamento());

        var captor = ArgumentCaptor.forClass(LancamentoRegistradoEvento.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.EXCHANGE),
                eq(RabbitConfig.ROUTING_KEY),
                captor.capture());

        var evento = captor.getValue();
        assertThat(evento.tipo()).isEqualTo("LancamentoRegistrado");
        assertThat(evento.versao()).isEqualTo("1.0");
        assertThat(evento.id()).isNotNull();
    }

    @Test
    void devePreencherPayloadComDadosDoLancamento() {
        publisher.publicarLancamentoRegistrado(umLancamento());

        var captor = ArgumentCaptor.forClass(LancamentoRegistradoEvento.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.ROUTING_KEY), captor.capture());

        var payload = captor.getValue().payload();
        assertThat(payload.lancamentoId()).isEqualTo(LANCAMENTO_ID);
        assertThat(payload.tipo()).isEqualTo("CREDITO");
        assertThat(payload.valor()).isEqualByComparingTo("150.00");
        assertThat(payload.dataCompetencia()).isEqualTo(LocalDate.of(2026, 5, 9));
        assertThat(payload.operadorId()).isEqualTo("usr_test");
    }

    private Lancamento umLancamento() {
        return Lancamento.reconstituir(
                LancamentoId.de(LANCAMENTO_ID),
                TipoLancamento.CREDITO,
                Valor.de("150.00"),
                "Venda",
                LocalDate.of(2026, 5, 9),
                "usr_test",
                LocalDateTime.of(2026, 5, 9, 12, 0));
    }
}
