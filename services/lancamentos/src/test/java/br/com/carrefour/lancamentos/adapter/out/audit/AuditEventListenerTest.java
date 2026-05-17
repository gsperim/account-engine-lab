package br.com.carrefour.lancamentos.adapter.out.audit;

import br.com.carrefour.lancamentos.adapter.out.persistence.AuditLogJpaEntity;
import br.com.carrefour.lancamentos.adapter.out.persistence.AuditLogJpaRepository;
import br.com.carrefour.lancamentos.domain.model.AuditEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("null") // Mockito matchers (capture/any) retornam null — incompatível com @NonNull do Spring Data
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
        var recursoId = UUID.randomUUID();
        var evento    = new AuditEvento("usr-123", "lancamento.registrado", recursoId,
                                        Map.of("tipo", "CREDITO", "valor", "150.00"));

        listener.handle(evento);

        var captor = ArgumentCaptor.forClass(AuditLogJpaEntity.class);
        verify(repository).save(captor.capture());

        var salvo = captor.getValue();
        assertThat(salvo.getOperadorId()).isEqualTo("usr-123");
        assertThat(salvo.getAcao()).isEqualTo("lancamento.registrado");
        assertThat(salvo.getRecursoId()).isEqualTo(recursoId);
        assertThat(salvo.getContexto()).contains("CREDITO").contains("150.00");
    }

    @Test
    void naoDevePropagарExcecaoQuandoRepositorioFalha() {
        var evento = new AuditEvento("usr-123", "lancamento.registrado", UUID.randomUUID(),
                                     Map.of("tipo", "CREDITO"));
        doThrow(new RuntimeException("DB fora")).when(repository).save(any());

        // falha silenciosa — operação de negócio já foi commitada
        assertThatNoException().isThrownBy(() -> listener.handle(evento));
    }
}
