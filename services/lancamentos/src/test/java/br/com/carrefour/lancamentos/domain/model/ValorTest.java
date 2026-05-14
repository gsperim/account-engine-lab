package br.com.carrefour.lancamentos.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class ValorTest {

    @Test
    void deveAceitarValorPositivoComDuasCasasDecimais() {
        var valor = Valor.de(new BigDecimal("150.00"));
        assertThat(valor.toBigDecimal()).isEqualByComparingTo("150.00");
    }

    @Test
    void deveAceitarValorSemCasasDecimaisEPreencherZeros() {
        var valor = Valor.de(new BigDecimal("150"));
        assertThat(valor.toBigDecimal()).isEqualByComparingTo("150.00");
    }

    @Test
    void deveLancarExcecaoParaValorNulo() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Valor.de((BigDecimal) null))
            .withMessage("Valor não pode ser nulo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.01"})
    void deveLancarExcecaoParaValorNaoPositivo(String valor) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Valor.de(new BigDecimal(valor)))
            .withMessage("Valor deve ser positivo");
    }

    @Test
    void deveLancarExcecaoParaValorComMaisDeDuasCasasDecimais() {
        assertThatThrownBy(() -> Valor.de(new BigDecimal("150.001")))
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void deveSomarCorretamente() {
        var a = Valor.de("100.00");
        var b = Valor.de("50.50");
        assertThat(a.somar(b)).isEqualTo(Valor.de("150.50"));
    }

    @Test
    void deveSubtrairCorretamente() {
        var a = Valor.de("150.50");
        var b = Valor.de("50.50");
        assertThat(a.subtrair(b)).isEqualTo(Valor.de("100.00"));
    }

    @Test
    void deveImplementarEqualidadePorValor() {
        assertThat(Valor.de("100.00")).isEqualTo(Valor.de("100.0"));
        assertThat(Valor.de("100.00")).isNotEqualTo(Valor.de("100.01"));
    }
}
