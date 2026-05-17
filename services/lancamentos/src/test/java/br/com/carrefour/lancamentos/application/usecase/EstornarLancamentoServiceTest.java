package br.com.carrefour.lancamentos.application.usecase;

import br.com.carrefour.lancamentos.domain.exception.LancamentoJaEstornadoException;
import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.model.Valor;
import br.com.carrefour.lancamentos.domain.port.in.EstornarLancamentoUseCase.Command;
import br.com.carrefour.lancamentos.domain.port.out.LancamentoRepository;
import br.com.carrefour.lancamentos.domain.port.out.OutboxPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstornarLancamentoServiceTest {

    @Mock LancamentoRepository repository;
    @Mock OutboxPort           outbox;

    EstornarLancamentoService service;

    static final UUID ORIGINAL_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final LocalDate DATA   = LocalDate.of(2026, 5, 9);

    @BeforeEach
    void setUp() {
        service = new EstornarLancamentoService(repository, outbox);
    }

    @Test
    void deveCriarEstornoComTipoInversoEMesmoValor() {
        var original = lancamentoCredito(ORIGINAL_ID, false);
        when(repository.buscarPorIdComLock(LancamentoId.de(ORIGINAL_ID))).thenReturn(Optional.of(original));
        when(repository.buscarPorId(argThat(id -> !id.toUUID().equals(ORIGINAL_ID)))).thenReturn(Optional.empty());
        when(repository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        var estorno = service.executar(new Command(LancamentoId.de(ORIGINAL_ID), "usr_test"));

        assertThat(estorno.getTipo()).isEqualTo(TipoLancamento.DEBITO);
        assertThat(estorno.getValor()).isEqualTo(Valor.de("150.00"));
        assertThat(estorno.getDataCompetencia()).isEqualTo(DATA);
        assertThat(estorno.getDescricao()).contains(ORIGINAL_ID.toString());
        assertThat(original.isEstornado()).isTrue();
        verify(outbox).registrar(estorno);
    }

    @Test
    void deveLancarExcecaoQuandoLancamentoNaoEncontrado() {
        when(repository.buscarPorIdComLock(any())).thenReturn(Optional.empty());

        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> service.executar(new Command(LancamentoId.de(ORIGINAL_ID), "usr_test")));

        verify(repository, never()).salvar(any());
        verify(outbox, never()).registrar(any());
    }

    @Test
    void deveLancarExcecaoQuandoJaEstornado() {
        var original = lancamentoCredito(ORIGINAL_ID, true);
        when(repository.buscarPorIdComLock(LancamentoId.de(ORIGINAL_ID))).thenReturn(Optional.of(original));

        assertThatExceptionOfType(LancamentoJaEstornadoException.class)
            .isThrownBy(() -> service.executar(new Command(LancamentoId.de(ORIGINAL_ID), "usr_test")));

        verify(repository, never()).salvar(any());
        verify(outbox, never()).registrar(any());
    }

    @Test
    void deveRetornarEstornoExistenteEmReplayIdempotente() {
        var original = lancamentoCredito(ORIGINAL_ID, false);
        var estornoExistente = lancamentoDebito(UUID.nameUUIDFromBytes(("estorno-" + ORIGINAL_ID).getBytes()));
        when(repository.buscarPorIdComLock(LancamentoId.de(ORIGINAL_ID))).thenReturn(Optional.of(original));
        when(repository.buscarPorId(argThat(id -> !id.toUUID().equals(ORIGINAL_ID)))).thenReturn(Optional.of(estornoExistente));

        var resultado = service.executar(new Command(LancamentoId.de(ORIGINAL_ID), "usr_test"));

        assertThat(resultado).isSameAs(estornoExistente);
        verify(repository, never()).salvar(any());
        verify(outbox, never()).registrar(any());
    }

    private Lancamento lancamentoCredito(UUID id, boolean estornado) {
        return Lancamento.reconstituir(LancamentoId.de(id), TipoLancamento.CREDITO,
                Valor.de("150.00"), "Venda", DATA, "usr_test", LocalDateTime.now(),
                "hash-test", estornado, estornado ? LocalDateTime.now() : null);
    }

    private Lancamento lancamentoDebito(UUID id) {
        return Lancamento.reconstituir(LancamentoId.de(id), TipoLancamento.DEBITO,
                Valor.de("150.00"), "Estorno de " + ORIGINAL_ID, DATA, "usr_test",
                LocalDateTime.now(), "hash-estorno", false, null);
    }
}
