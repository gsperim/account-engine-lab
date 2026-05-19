package br.com.carrefour.consolidado.adapter.out.audit;

import br.com.carrefour.consolidado.adapter.out.persistence.AuditLogJpaEntity;
import br.com.carrefour.consolidado.adapter.out.persistence.AuditLogJpaRepository;
import br.com.carrefour.consolidado.domain.model.AuditEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock AuditLogJpaRepository repository;

    AuditEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuditEventListener(repository, new ObjectMapper());
    }

    @Test
    void devePersistirRegistroComCamposCorretos() {
        var evento = new AuditEvento("usr-admin", "consolidado.reconstruido", null,
                Map.of("data_inicio", "2026-05-01", "datas_processadas", "9", "divergencias_corrigidas", "1"));

        listener.handle(evento);

        var captor = ArgumentCaptor.forClass(AuditLogJpaEntity.class);
        verify(repository).save(captor.capture());

        var salvo = captor.getValue();
        assertThat(salvo.getOperadorId()).isEqualTo("usr-admin");
        assertThat(salvo.getAcao()).isEqualTo("consolidado.reconstruido");
        assertThat(salvo.getRecursoId()).isNull();
        assertThat(salvo.getContexto()).contains("datas_processadas").contains("9");
    }

    @Test
    void naoDevePropagарExcecaoQuandoRepositorioFalha() {
        var evento = new AuditEvento("usr-admin", "consolidado.reconstruido", null, Map.of());
        doThrow(new RuntimeException("DB fora")).when(repository).save(any());

        assertThatNoException().isThrownBy(() -> listener.handle(evento));
    }
}
