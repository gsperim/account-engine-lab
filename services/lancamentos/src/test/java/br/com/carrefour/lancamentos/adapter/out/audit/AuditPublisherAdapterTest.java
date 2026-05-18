package br.com.carrefour.lancamentos.adapter.out.audit;

import br.com.carrefour.lancamentos.domain.model.AuditEvento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuditPublisherAdapterTest {

    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks AuditPublisherAdapter adapter;

    @Test
    void registrar_devePublicarApplicationEvent() {
        var evento = new AuditEvento("operador-1", "REGISTRAR_LANCAMENTO", UUID.randomUUID(), Map.of("tipo", "CREDITO"));

        adapter.registrar(evento);

        verify(eventPublisher).publishEvent(evento);
    }

    @Test
    void registrar_deveLancarNullPointerParaEventoNulo() {
        assertThatThrownBy(() -> adapter.registrar(null))
            .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(eventPublisher);
    }
}
