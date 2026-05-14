package br.com.carrefour.lancamentos.adapter.out.persistence;

import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.model.Valor;
import br.com.carrefour.lancamentos.domain.model.Lancamento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(LancamentoRepositoryAdapter.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class LancamentoRepositoryAdapterTest {

    @Autowired LancamentoRepositoryAdapter adapter;

    static final LocalDate HOJE = LocalDate.of(2026, 5, 9);

    @Test
    void deveSalvarEBuscarPorId() {
        var lancamento = umLancamento(UUID.randomUUID());

        var salvo = adapter.salvar(lancamento);
        var encontrado = adapter.buscarPorId(salvo.getId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getId()).isEqualTo(salvo.getId());
        assertThat(encontrado.get().getTipo()).isEqualTo(TipoLancamento.CREDITO);
        assertThat(encontrado.get().getValor()).isEqualTo(Valor.de("150.00"));
    }

    @Test
    void deveRetornarVazioParaIdInexistente() {
        var resultado = adapter.buscarPorId(LancamentoId.de(UUID.randomUUID()));
        assertThat(resultado).isEmpty();
    }

    @Test
    void deveDetectarIdExistente() {
        var id = UUID.randomUUID();
        adapter.salvar(umLancamento(id));

        assertThat(adapter.existePorId(LancamentoId.de(id))).isTrue();
        assertThat(adapter.existePorId(LancamentoId.de(UUID.randomUUID()))).isFalse();
    }

    @Test
    void deveBuscarPorDataCompetencia() {
        adapter.salvar(umLancamento(UUID.randomUUID()));
        adapter.salvar(umLancamentoDebito(UUID.randomUUID()));

        var todos = adapter.buscarPorDataCompetencia(HOJE, null, 0, 20);
        var soCredito = adapter.buscarPorDataCompetencia(HOJE, TipoLancamento.CREDITO, 0, 20);

        assertThat(todos).hasSize(2);
        assertThat(soCredito).hasSize(1);
        assertThat(soCredito.get(0).getTipo()).isEqualTo(TipoLancamento.CREDITO);
    }

    @Test
    void deveContarPorDataCompetencia() {
        adapter.salvar(umLancamento(UUID.randomUUID()));
        adapter.salvar(umLancamento(UUID.randomUUID()));
        adapter.salvar(umLancamentoDebito(UUID.randomUUID()));

        assertThat(adapter.contarPorDataCompetencia(HOJE, null)).isEqualTo(3);
        assertThat(adapter.contarPorDataCompetencia(HOJE, TipoLancamento.CREDITO)).isEqualTo(2);
        assertThat(adapter.contarPorDataCompetencia(HOJE, TipoLancamento.DEBITO)).isEqualTo(1);
    }

    private Lancamento umLancamento(UUID id) {
        return Lancamento.criar(
                LancamentoId.de(id), TipoLancamento.CREDITO,
                Valor.de("150.00"), "Venda", HOJE, "usr_test");
    }

    private Lancamento umLancamentoDebito(UUID id) {
        return Lancamento.criar(
                LancamentoId.de(id), TipoLancamento.DEBITO,
                Valor.de("50.00"), "Despesa", HOJE, "usr_test");
    }
}
