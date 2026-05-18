package br.com.carrefour.lancamentos.adapter.out.messaging;

import br.com.carrefour.lancamentos.adapter.out.persistence.OutboxJpaEntity;
import br.com.carrefour.lancamentos.adapter.out.persistence.OutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock OutboxJpaRepository outboxRepo;
    @Mock OutboxPublisher publisher;
    @InjectMocks OutboxRelay relay;

    @Test
    void processar_devePublicarEMarcarComoPublicado() throws Exception {
        var entry = umEntryPendente();
        when(outboxRepo.buscarPendentes()).thenReturn(List.of(entry));

        relay.processar();

        verify(publisher).publicar(entry.getPayload(), entry.getLancamentoId());
        assertThat(entry.isPublicado()).isTrue();
        verify(outboxRepo).save(entry);
    }

    @Test
    void processar_deveIncrementarTentativasEmFalha() throws Exception {
        var entry = umEntryPendente();
        when(outboxRepo.buscarPendentes()).thenReturn(List.of(entry));
        doThrow(new RuntimeException("broker fora")).when(publisher).publicar(any(), any());

        relay.processar();

        assertThat(entry.getTentativas()).isEqualTo(1);
        assertThat(entry.isPublicado()).isFalse();
        verify(outboxRepo).save(entry);
    }

    @Test
    void processar_deveRetornarSemFazerNadaQuandoSemPendentes() throws Exception {
        when(outboxRepo.buscarPendentes()).thenReturn(List.of());

        relay.processar();

        verifyNoInteractions(publisher);
        verify(outboxRepo, never()).save(any(OutboxJpaEntity.class));
    }

    @Test
    void limparPublicados_deveChamarDeleteComLimiteCorreto() {
        when(outboxRepo.deletarPublicadosAntes(any())).thenReturn(5);

        relay.limparPublicados();

        var captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxRepo).deletarPublicadosAntes(captor.capture());
        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusDays(6));
    }

    private OutboxJpaEntity umEntryPendente() {
        return new OutboxJpaEntity(UUID.randomUUID(), UUID.randomUUID(), "LancamentoRegistrado", "{\"id\":\"1\"}");
    }
}
