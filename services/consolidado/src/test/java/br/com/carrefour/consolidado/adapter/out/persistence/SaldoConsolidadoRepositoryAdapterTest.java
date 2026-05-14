package br.com.carrefour.consolidado.adapter.out.persistence;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(SaldoConsolidadoRepositoryAdapter.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SaldoConsolidadoRepositoryAdapterTest {

    @Autowired SaldoConsolidadoRepositoryAdapter adapter;

    static final LocalDate HOJE    = LocalDate.of(2026, 5, 9);
    static final LocalDate ONTEM   = LocalDate.of(2026, 5, 8);
    static final LocalDate ANTEONTEM = LocalDate.of(2026, 5, 7);

    @Test
    void deveSalvarEBuscarPorData() {
        var saldo = SaldoConsolidado.novo(HOJE);
        saldo.aplicarCredito(new BigDecimal("150.00"));

        adapter.salvar(saldo);
        var encontrado = adapter.buscarPorData(HOJE);

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getTotalCreditos()).isEqualByComparingTo("150.00");
        assertThat(encontrado.get().getTotalLancamentos()).isEqualTo(1);
    }

    @Test
    void deveRetornarVazioParaDataSemSaldo() {
        assertThat(adapter.buscarPorData(HOJE)).isEmpty();
    }

    @Test
    void deveBuscarPorPeriodoOrdenado() {
        adapter.salvar(SaldoConsolidado.novo(HOJE));
        adapter.salvar(SaldoConsolidado.novo(ONTEM));
        adapter.salvar(SaldoConsolidado.novo(ANTEONTEM));

        var resultado = adapter.buscarPorPeriodo(ANTEONTEM, HOJE);

        assertThat(resultado).hasSize(3);
        assertThat(resultado.get(0).getData()).isEqualTo(ANTEONTEM);
        assertThat(resultado.get(2).getData()).isEqualTo(HOJE);
    }

    @Test
    void deveAtualizarSaldoExistente() {
        var saldo = SaldoConsolidado.novo(HOJE);
        saldo.aplicarCredito(new BigDecimal("100.00"));
        adapter.salvar(saldo);

        var salvoAtualizado = adapter.buscarPorData(HOJE).get();
        salvoAtualizado.aplicarDebito(new BigDecimal("40.00"));
        adapter.salvar(salvoAtualizado);

        var final_ = adapter.buscarPorData(HOJE).get();
        assertThat(final_.getSaldo()).isEqualByComparingTo("60.00");
        assertThat(final_.getTotalLancamentos()).isEqualTo(2);
    }
}
