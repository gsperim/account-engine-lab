package br.com.carrefour.consolidado.application.usecase;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.port.in.ReconstruirConsolidadoUseCase;
import br.com.carrefour.consolidado.domain.port.out.AuditPublisher;
import br.com.carrefour.consolidado.domain.port.out.LancamentosGateway;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconstruirConsolidadoServiceTest {

    @Mock LancamentosGateway        lancamentosGateway;
    @Mock SaldoConsolidadoRepository repository;
    @Mock ReconstruirPorDataHelper  helper;
    @Mock AuditPublisher            auditPublisher;

    ReconstruirConsolidadoService service;

    static final LocalDate INICIO = LocalDate.of(2026, 5, 1);
    static final LocalDate FIM    = LocalDate.of(2026, 5, 3);

    @BeforeEach
    void setUp() {
        service = new ReconstruirConsolidadoService(lancamentosGateway, repository, helper, auditPublisher);
    }

    @Test
    void deveProcessarTodasAsDatasNoPeriodo() {
        when(lancamentosGateway.buscarResumoDiario(any())).thenReturn(
                new LancamentosGateway.ResumoDiario(INICIO, new BigDecimal("100.00"), new BigDecimal("50.00"), 3));
        when(repository.buscarPorData(any())).thenReturn(Optional.of(
                SaldoConsolidado.reconstituir(INICIO, new BigDecimal("100.00"), new BigDecimal("50.00"), 3, LocalDateTime.now())));

        var resultado = service.executar(new ReconstruirConsolidadoUseCase.Command(INICIO, FIM, "usr_admin"));

        assertThat(resultado.datasProcessadas()).isEqualTo(3);
        assertThat(resultado.divergenciasCorrigidas()).isZero();
        assertThat(resultado.dataInicio()).isEqualTo(INICIO);
        assertThat(resultado.dataFim()).isEqualTo(FIM);
        verify(helper, times(3)).salvarEEvictCache(any(), any());
        verify(auditPublisher).registrar(any());
    }

    @Test
    void deveContinuarSeUmaDataFalhar() {
        when(lancamentosGateway.buscarResumoDiario(any()))
                .thenReturn(new LancamentosGateway.ResumoDiario(INICIO, new BigDecimal("100.00"), BigDecimal.ZERO, 1))
                .thenThrow(new RuntimeException("gateway indisponível"))
                .thenReturn(new LancamentosGateway.ResumoDiario(INICIO, new BigDecimal("50.00"), BigDecimal.ZERO, 1));
        when(repository.buscarPorData(any())).thenReturn(Optional.empty());

        var resultado = service.executar(new ReconstruirConsolidadoUseCase.Command(INICIO, FIM, "usr_admin"));

        assertThat(resultado.datasProcessadas()).isEqualTo(2);
        verify(helper, times(2)).salvarEEvictCache(any(), any());
    }

    @Test
    void deveContarDivergenciasCorrigidas() {
        when(lancamentosGateway.buscarResumoDiario(any())).thenReturn(
                new LancamentosGateway.ResumoDiario(INICIO, new BigDecimal("200.00"), new BigDecimal("50.00"), 2));
        when(repository.buscarPorData(any())).thenReturn(Optional.of(
                SaldoConsolidado.reconstituir(INICIO, new BigDecimal("100.00"), new BigDecimal("50.00"), 1, LocalDateTime.now())));

        var resultado = service.executar(new ReconstruirConsolidadoUseCase.Command(INICIO, FIM, "usr_admin"));

        assertThat(resultado.divergenciasCorrigidas()).isEqualTo(3);
    }

    @Test
    void deveLancarExcecaoParaPeriodoInvalido() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.executar(new ReconstruirConsolidadoUseCase.Command(FIM, INICIO, "usr_admin")));
    }
}
