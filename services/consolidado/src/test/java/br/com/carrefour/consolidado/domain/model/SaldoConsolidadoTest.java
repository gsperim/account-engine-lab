package br.com.carrefour.consolidado.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class SaldoConsolidadoTest {

    static final LocalDate HOJE = LocalDate.of(2026, 5, 9);

    @Test
    void deveCriarSaldoZeradoParaNovaData() {
        var saldo = SaldoConsolidado.novo(HOJE);

        assertThat(saldo.getData()).isEqualTo(HOJE);
        assertThat(saldo.getTotalCreditos()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saldo.getTotalDebitos()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saldo.getSaldo()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saldo.getTotalLancamentos()).isZero();
    }

    @Test
    void deveLancarExcecaoSemData() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SaldoConsolidado.novo(null))
            .withMessage("Data é obrigatória");
    }

    @Test
    void deveAcumularCreditos() {
        var saldo = SaldoConsolidado.novo(HOJE);
        saldo.aplicarCredito(new BigDecimal("100.00"));
        saldo.aplicarCredito(new BigDecimal("50.00"));

        assertThat(saldo.getTotalCreditos()).isEqualByComparingTo("150.00");
        assertThat(saldo.getTotalLancamentos()).isEqualTo(2);
        assertThat(saldo.getSaldo()).isEqualByComparingTo("150.00");
    }

    @Test
    void deveAcumularDebitos() {
        var saldo = SaldoConsolidado.novo(HOJE);
        saldo.aplicarDebito(new BigDecimal("30.00"));

        assertThat(saldo.getTotalDebitos()).isEqualByComparingTo("30.00");
        assertThat(saldo.getSaldo()).isEqualByComparingTo("-30.00");
        assertThat(saldo.getTotalLancamentos()).isEqualTo(1);
    }

    @Test
    void deveCalcularSaldoMisto() {
        var saldo = SaldoConsolidado.novo(HOJE);
        saldo.aplicarCredito(new BigDecimal("200.00"));
        saldo.aplicarDebito(new BigDecimal("80.00"));

        assertThat(saldo.getSaldo()).isEqualByComparingTo("120.00");
        assertThat(saldo.getTotalLancamentos()).isEqualTo(2);
    }

    @Test
    void deveReconstituirComValoresExatos() {
        var saldo = SaldoConsolidado.reconstituir(
            HOJE, new BigDecimal("500.00"), new BigDecimal("200.00"), 5, java.time.LocalDateTime.now());

        assertThat(saldo.getTotalCreditos()).isEqualByComparingTo("500.00");
        assertThat(saldo.getTotalDebitos()).isEqualByComparingTo("200.00");
        assertThat(saldo.getSaldo()).isEqualByComparingTo("300.00");
        assertThat(saldo.getTotalLancamentos()).isEqualTo(5);
    }

    @Test
    void deveLancarExcecaoAoAplicarCreditoNulo() {
        var saldo = SaldoConsolidado.novo(HOJE);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> saldo.aplicarCredito(null))
            .withMessage("Valor do crédito deve ser positivo");
    }

    @Test
    void deveLancarExcecaoAoAplicarCreditoZero() {
        var saldo = SaldoConsolidado.novo(HOJE);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> saldo.aplicarCredito(BigDecimal.ZERO))
            .withMessage("Valor do crédito deve ser positivo");
    }

    @Test
    void deveLancarExcecaoAoAplicarDebitoNulo() {
        var saldo = SaldoConsolidado.novo(HOJE);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> saldo.aplicarDebito(null))
            .withMessage("Valor do débito deve ser positivo");
    }

    @Test
    void deveLancarExcecaoAoAplicarDebitoNegativo() {
        var saldo = SaldoConsolidado.novo(HOJE);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> saldo.aplicarDebito(new BigDecimal("-10.00")))
            .withMessage("Valor do débito deve ser positivo");
    }
}
