package br.com.carrefour.consolidado.application.usecase;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.model.TipoMovimento;
import br.com.carrefour.consolidado.domain.port.in.ProcessarLancamentoUseCase.Command;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessarLancamentoServiceTest {

    @Mock SaldoConsolidadoRepository repository;
    @InjectMocks ProcessarLancamentoService service;

    static final LocalDate HOJE = LocalDate.of(2026, 5, 9);

    @Test
    void deveCriarNovoSaldoQuandoNaoExiste() {
        when(repository.buscarPorData(HOJE)).thenReturn(Optional.empty());
        when(repository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        service.executar(new Command(UUID.randomUUID(), TipoMovimento.CREDITO, new BigDecimal("100.00"), HOJE));

        var captor = ArgumentCaptor.forClass(SaldoConsolidado.class);
        verify(repository).salvar(captor.capture());
        assertThat(captor.getValue().getTotalCreditos()).isEqualByComparingTo("100.00");
        assertThat(captor.getValue().getTotalLancamentos()).isEqualTo(1);
    }

    @Test
    void deveAcumularEmSaldoExistente() {
        var saldoExistente = SaldoConsolidado.novo(HOJE);
        saldoExistente.aplicarCredito(new BigDecimal("50.00"));
        when(repository.buscarPorData(HOJE)).thenReturn(Optional.of(saldoExistente));
        when(repository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        service.executar(new Command(UUID.randomUUID(), TipoMovimento.CREDITO, new BigDecimal("100.00"), HOJE));

        var captor = ArgumentCaptor.forClass(SaldoConsolidado.class);
        verify(repository).salvar(captor.capture());
        assertThat(captor.getValue().getTotalCreditos()).isEqualByComparingTo("150.00");
        assertThat(captor.getValue().getTotalLancamentos()).isEqualTo(2);
    }

    @Test
    void deveAplicarDebitoCorretamente() {
        when(repository.buscarPorData(HOJE)).thenReturn(Optional.empty());
        when(repository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        service.executar(new Command(UUID.randomUUID(), TipoMovimento.DEBITO, new BigDecimal("30.00"), HOJE));

        var captor = ArgumentCaptor.forClass(SaldoConsolidado.class);
        verify(repository).salvar(captor.capture());
        assertThat(captor.getValue().getTotalDebitos()).isEqualByComparingTo("30.00");
        assertThat(captor.getValue().getTotalCreditos()).isEqualByComparingTo("0.00");
    }
}
