package br.com.carrefour.consolidado.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null") // falso positivo: existsById/@NonNull UUID + save/@NonNull entity em contexto Mockito
@ExtendWith(MockitoExtension.class)
class LancamentosAplicadosRepositoryAdapterTest {

    @Mock LancamentosAplicadosJpaRepository jpaRepo;
    @InjectMocks LancamentosAplicadosRepositoryAdapter adapter;

    @Test
    void existePorId_deveRetornarTrueQuandoExiste() {
        var id = UUID.randomUUID();
        when(jpaRepo.existsById(id)).thenReturn(true);

        assertThat(adapter.existePorId(id)).isTrue();
    }

    @Test
    void existePorId_deveRetornarFalseQuandoNaoExiste() {
        var id = UUID.randomUUID();
        when(jpaRepo.existsById(id)).thenReturn(false);

        assertThat(adapter.existePorId(id)).isFalse();
    }

    @Test
    void registrar_deveSalvarEntidadeComDadosCorretos() {
        var id = UUID.randomUUID();
        var data = LocalDate.of(2026, 5, 9);

        adapter.registrar(id, data);

        var captor = ArgumentCaptor.forClass(LancamentosAplicadosJpaEntity.class);
        verify(jpaRepo).save(captor.capture());
        assertThat(captor.getValue().getLancamentoId()).isEqualTo(id);
        assertThat(captor.getValue().getDataCompetencia()).isEqualTo(data);
        assertThat(captor.getValue().getAplicadoEm()).isNotNull();
    }
}
