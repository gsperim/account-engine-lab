package br.com.carrefour.consolidado.application.usecase;

import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscarConsolidadoPeriodoServiceTest {

    @Mock SaldoConsolidadoRepository repository;
    @InjectMocks BuscarConsolidadoPeriodoService service;

    static final LocalDate INICIO = LocalDate.of(2026, 5, 1);
    static final LocalDate FIM    = LocalDate.of(2026, 5, 9);

    @Test
    void deveRetornarSaldosNoPeriodo() {
        when(repository.buscarPorPeriodo(INICIO, FIM)).thenReturn(List.of());

        var resultado = service.executar(INICIO, FIM);

        assertThat(resultado).isEmpty();
    }

    @Test
    void deveLancarExcecaoQuandoFimAntesDeInicio() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.executar(FIM, INICIO))
            .withMessageContaining("data_fim");
    }

    @Test
    void deveLancarExcecaoQuandoPeriodoMaiorQue90Dias() {
        var inicio = LocalDate.of(2026, 1, 1);
        var fim    = LocalDate.of(2026, 4, 5); // 94 dias

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.executar(inicio, fim))
            .withMessageContaining("90 dias");
    }
}
