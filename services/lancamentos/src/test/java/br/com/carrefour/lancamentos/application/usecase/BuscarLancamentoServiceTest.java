package br.com.carrefour.lancamentos.application.usecase;

import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.model.Valor;
import br.com.carrefour.lancamentos.domain.port.out.LancamentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscarLancamentoServiceTest {

    @Mock LancamentoRepository repository;
    @InjectMocks BuscarLancamentoService service;

    static final LancamentoId ID = LancamentoId.de(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @Test
    void deveRetornarLancamentoQuandoExiste() {
        var lancamento = umLancamento();
        when(repository.buscarPorId(ID)).thenReturn(Optional.of(lancamento));

        var resultado = service.executar(ID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(ID);
    }

    @Test
    void deveRetornarVazioQuandoNaoExiste() {
        when(repository.buscarPorId(ID)).thenReturn(Optional.empty());

        assertThat(service.executar(ID)).isEmpty();
    }

    private Lancamento umLancamento() {
        return Lancamento.reconstituir(ID, TipoLancamento.CREDITO, Valor.de("100.00"),
                null, LocalDate.of(2026, 5, 9), "usr_test", LocalDateTime.now());
    }
}
