package br.com.carrefour.lancamentos.adapter.out.persistence;

import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.Valor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRepositoryAdapterTest {

    @Mock OutboxJpaRepository repo;
    @Mock ObjectMapper mapper;
    @InjectMocks OutboxRepositoryAdapter adapter;

    @Test
    void registrar_devePersistirEventoNaTabela() throws Exception {
        when(mapper.writeValueAsString(any())).thenReturn("{\"id\":\"x\"}");

        adapter.registrar(umLancamento());

        verify(repo).save(notNull());
    }

    @Test
    void registrar_devePropagrarExcecaoDeSerializacao() throws Exception {
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("falha") {});

        assertThatThrownBy(() -> adapter.registrar(umLancamento()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Falha ao serializar");
    }

    private Lancamento umLancamento() {
        return Lancamento.reconstituir(
            LancamentoId.de(UUID.randomUUID()),
            TipoLancamento.CREDITO,
            Valor.de("100.00"),
            "Venda",
            LocalDate.of(2026, 5, 9),
            "operador-1",
            LocalDateTime.now(),
            "hash123",
            false, null);
    }
}
