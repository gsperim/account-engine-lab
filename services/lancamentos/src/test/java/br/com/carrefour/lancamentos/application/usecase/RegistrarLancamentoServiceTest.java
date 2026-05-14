package br.com.carrefour.lancamentos.application.usecase;

import br.com.carrefour.lancamentos.domain.exception.LancamentoDuplicadoException;
import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.model.Valor;
import br.com.carrefour.lancamentos.domain.port.in.RegistrarLancamentoUseCase.Command;
import br.com.carrefour.lancamentos.domain.port.out.LancamentoRepository;
import br.com.carrefour.lancamentos.domain.port.out.OutboxPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrarLancamentoServiceTest {

    @Mock LancamentoRepository repository;
    @Mock OutboxPort           outbox;

    RegistrarLancamentoService service;

    static final UUID KEY_UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID KEY_DUP  = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        service = new RegistrarLancamentoService(repository, outbox);
    }

    @Test
    void deveRegistrarLancamentoESalvarEEnfileirarNoOutbox() {
        when(repository.existePorId(LancamentoId.de(KEY_UUID))).thenReturn(false);
        when(repository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.executar(umCommand(KEY_UUID));

        assertThat(resultado.getId()).isEqualTo(LancamentoId.de(KEY_UUID));
        assertThat(resultado.getTipo()).isEqualTo(TipoLancamento.CREDITO);
        assertThat(resultado.getValor()).isEqualTo(Valor.de("150.00"));
        verify(repository).salvar(any(Lancamento.class));
        verify(outbox).registrar(any(Lancamento.class));
    }

    @Test
    void deveLancarExcecaoQuandoIdJaExiste() {
        when(repository.existePorId(LancamentoId.de(KEY_DUP))).thenReturn(true);

        assertThatExceptionOfType(LancamentoDuplicadoException.class)
            .isThrownBy(() -> service.executar(umCommand(KEY_DUP)));

        verify(repository, never()).salvar(any());
        verify(outbox, never()).registrar(any());
    }

    @Test
    void naoDeveEnfileirarNoOutboxSeRepositorioFalhar() {
        when(repository.existePorId(any())).thenReturn(false);
        when(repository.salvar(any())).thenThrow(new RuntimeException("Banco indisponível"));

        assertThatThrownBy(() -> service.executar(umCommand(KEY_UUID)))
            .isInstanceOf(RuntimeException.class);

        verify(outbox, never()).registrar(any());
    }

    private Command umCommand(UUID key) {
        return new Command(
            TipoLancamento.CREDITO,
            Valor.de("150.00"),
            "Venda balcão",
            LocalDate.of(2026, 5, 9),
            "usr_abc123",
            key.toString()
        );
    }
}
