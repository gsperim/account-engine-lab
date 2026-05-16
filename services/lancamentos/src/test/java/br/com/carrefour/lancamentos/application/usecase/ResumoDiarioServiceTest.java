package br.com.carrefour.lancamentos.application.usecase;

import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.port.in.ResumoDiarioUseCase;
import br.com.carrefour.lancamentos.domain.port.out.LancamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumoDiarioServiceTest {

    @Mock LancamentoRepository repository;

    ResumoDiarioService service;

    static final LocalDate DATA = LocalDate.of(2026, 5, 9);

    @BeforeEach
    void setUp() {
        service = new ResumoDiarioService(repository);
    }

    @Test
    void deveRetornarTotaisCorretos() {
        when(repository.somarValorPorDataETipo(DATA, TipoLancamento.CREDITO)).thenReturn(new BigDecimal("3250.00"));
        when(repository.somarValorPorDataETipo(DATA, TipoLancamento.DEBITO)).thenReturn(new BigDecimal("1180.50"));
        when(repository.contarPorDataCompetencia(DATA, null)).thenReturn(14L);

        var resultado = service.executar(new ResumoDiarioUseCase.Query(DATA));

        assertThat(resultado.data()).isEqualTo(DATA);
        assertThat(resultado.totalCreditos()).isEqualByComparingTo("3250.00");
        assertThat(resultado.totalDebitos()).isEqualByComparingTo("1180.50");
        assertThat(resultado.totalLancamentos()).isEqualTo(14L);
    }

    @Test
    void deveRetornarZeroQuandoNaoHaLancamentos() {
        when(repository.somarValorPorDataETipo(DATA, TipoLancamento.CREDITO)).thenReturn(null);
        when(repository.somarValorPorDataETipo(DATA, TipoLancamento.DEBITO)).thenReturn(null);
        when(repository.contarPorDataCompetencia(DATA, null)).thenReturn(0L);

        var resultado = service.executar(new ResumoDiarioUseCase.Query(DATA));

        assertThat(resultado.totalCreditos()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resultado.totalDebitos()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resultado.totalLancamentos()).isZero();
    }
}
