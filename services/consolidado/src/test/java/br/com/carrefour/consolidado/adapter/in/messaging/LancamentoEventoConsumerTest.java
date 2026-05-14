package br.com.carrefour.consolidado.adapter.in.messaging;

import br.com.carrefour.consolidado.domain.model.TipoMovimento;
import br.com.carrefour.consolidado.domain.port.in.ProcessarLancamentoUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LancamentoEventoConsumerTest {

    @Mock ProcessarLancamentoUseCase processarUseCase;
    @InjectMocks LancamentoEventoConsumer consumer;

    static final UUID LANCAMENTO_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final LocalDate HOJE = LocalDate.of(2026, 5, 9);

    @Test
    void deveConverterEventoEmCommandEProcessar() {
        consumer.consumir(umEvento("CREDITO", new BigDecimal("150.00")));

        var captor = ArgumentCaptor.forClass(ProcessarLancamentoUseCase.Command.class);
        verify(processarUseCase).executar(captor.capture());

        var command = captor.getValue();
        assertThat(command.lancamentoId()).isEqualTo(LANCAMENTO_ID);
        assertThat(command.tipo()).isEqualTo(TipoMovimento.CREDITO);
        assertThat(command.valor()).isEqualByComparingTo("150.00");
        assertThat(command.dataCompetencia()).isEqualTo(HOJE);
    }

    @Test
    void deveMapearDebitoCorretamente() {
        consumer.consumir(umEvento("DEBITO", new BigDecimal("50.00")));

        var captor = ArgumentCaptor.forClass(ProcessarLancamentoUseCase.Command.class);
        verify(processarUseCase).executar(captor.capture());
        assertThat(captor.getValue().tipo()).isEqualTo(TipoMovimento.DEBITO);
    }

    private LancamentoRegistradoEvento umEvento(String tipo, BigDecimal valor) {
        var payload = new LancamentoRegistradoEvento.Payload(
                LANCAMENTO_ID, tipo, valor, HOJE, "usr_test", OffsetDateTime.now());
        return new LancamentoRegistradoEvento(
                UUID.randomUUID(), "LancamentoRegistrado", "1.0", OffsetDateTime.now(), payload);
    }
}
