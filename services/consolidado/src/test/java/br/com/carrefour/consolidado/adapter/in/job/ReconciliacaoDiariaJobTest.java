package br.com.carrefour.consolidado.adapter.in.job;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.port.out.LancamentosGateway;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliacaoDiariaJobTest {

    @Mock LancamentosGateway lancamentosGateway;
    @Mock SaldoConsolidadoRepository repository;

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ReconciliacaoDiariaJob job;

    static final LocalDate ONTEM = LocalDate.now().minusDays(1);

    @BeforeEach
    void setUp() {
        job = new ReconciliacaoDiariaJob(lancamentosGateway, repository, meterRegistry);
    }

    @Test
    void deveLogarOkQuandoSaldosConferem() {
        when(lancamentosGateway.buscarResumoDiario(any())).thenReturn(
                new LancamentosGateway.ResumoDiario(ONTEM, new BigDecimal("1000.00"), new BigDecimal("500.00"), 5));
        when(repository.buscarPorData(any())).thenReturn(Optional.of(
                SaldoConsolidado.reconstituir(ONTEM, new BigDecimal("1000.00"), new BigDecimal("500.00"), 5, LocalDateTime.now())));

        job.reconciliar();

        assertThat(meterRegistry.counter("saldo_reconciliado_divergencias_total").count()).isZero();
    }

    @Test
    void deveIncrementarContadorQuandoHaDivergencia() {
        when(lancamentosGateway.buscarResumoDiario(any())).thenReturn(
                new LancamentosGateway.ResumoDiario(ONTEM, new BigDecimal("1000.00"), new BigDecimal("500.00"), 5));
        when(repository.buscarPorData(any())).thenReturn(Optional.of(
                SaldoConsolidado.reconstituir(ONTEM, new BigDecimal("900.00"), new BigDecimal("500.00"), 4, LocalDateTime.now())));

        job.reconciliar();

        assertThat(meterRegistry.counter("saldo_reconciliado_divergencias_total").count()).isEqualTo(1.0);
    }

    @Test
    void deveIncrementarContadorQuandoSaldoAusenteComLancamentos() {
        when(lancamentosGateway.buscarResumoDiario(any())).thenReturn(
                new LancamentosGateway.ResumoDiario(ONTEM, new BigDecimal("100.00"), BigDecimal.ZERO, 2));
        when(repository.buscarPorData(any())).thenReturn(Optional.empty());

        job.reconciliar();

        assertThat(meterRegistry.counter("saldo_reconciliado_divergencias_total").count()).isEqualTo(1.0);
    }

    @Test
    void naoDevePropagararExcecaoQuandoGatewayFalha() {
        when(lancamentosGateway.buscarResumoDiario(any())).thenThrow(new RuntimeException("timeout"));

        job.reconciliar(); // não deve lançar exceção

        assertThat(meterRegistry.counter("saldo_reconciliado_divergencias_total").count()).isZero();
    }
}
