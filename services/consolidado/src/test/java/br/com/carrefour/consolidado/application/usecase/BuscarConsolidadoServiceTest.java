package br.com.carrefour.consolidado.application.usecase;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscarConsolidadoServiceTest {

    @Mock SaldoConsolidadoRepository repository;
    @InjectMocks BuscarConsolidadoService service;

    static final LocalDate HOJE = LocalDate.of(2026, 5, 9);

    @Test
    void deveRetornarSaldoQuandoExiste() {
        var saldo = SaldoConsolidado.novo(HOJE);
        when(repository.buscarPorData(HOJE)).thenReturn(Optional.of(saldo));

        assertThat(service.executar(HOJE)).contains(saldo);
    }

    @Test
    void deveRetornarVazioQuandoNaoExiste() {
        when(repository.buscarPorData(any())).thenReturn(Optional.empty());

        assertThat(service.executar(HOJE)).isEmpty();
    }
}
