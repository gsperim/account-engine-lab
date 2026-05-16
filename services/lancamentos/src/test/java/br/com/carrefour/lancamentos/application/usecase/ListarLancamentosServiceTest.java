package br.com.carrefour.lancamentos.application.usecase;

import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.model.Valor;
import br.com.carrefour.lancamentos.domain.port.in.ListarLancamentosUseCase.Query;
import br.com.carrefour.lancamentos.domain.port.out.LancamentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarLancamentosServiceTest {

    @Mock LancamentoRepository repository;
    @InjectMocks ListarLancamentosService service;

    static final LocalDate HOJE = LocalDate.of(2026, 5, 9);

    @Test
    void deveRetornarPaginaComTotais() {
        var lancamento = umLancamento();
        when(repository.buscarPorDataCompetencia(HOJE, null, 0, 20)).thenReturn(List.of(lancamento));
        when(repository.contarPorDataCompetencia(HOJE, null)).thenReturn(1L);

        var resultado = service.executar(new Query(HOJE, null, 0, 20));

        assertThat(resultado.items()).hasSize(1);
        assertThat(resultado.total()).isEqualTo(1L);
        assertThat(resultado.totalPages()).isEqualTo(1);
    }

    @Test
    void deveFiltrarPorTipo() {
        when(repository.buscarPorDataCompetencia(HOJE, TipoLancamento.DEBITO, 0, 20)).thenReturn(List.of());
        when(repository.contarPorDataCompetencia(HOJE, TipoLancamento.DEBITO)).thenReturn(0L);

        var resultado = service.executar(new Query(HOJE, TipoLancamento.DEBITO, 0, 20));

        assertThat(resultado.items()).isEmpty();
        assertThat(resultado.totalPages()).isEqualTo(0);
    }

    private Lancamento umLancamento() {
        return Lancamento.reconstituir(
                LancamentoId.de(UUID.randomUUID()), TipoLancamento.CREDITO,
                Valor.de("100.00"), null, HOJE, "usr_test", LocalDateTime.now(), "test-hash");
    }
}
