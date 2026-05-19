package br.com.carrefour.consolidado.adapter.out.audit;

import br.com.carrefour.consolidado.domain.model.AuditEvento;
import br.com.carrefour.consolidado.domain.port.out.AuditPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AuditPublisherAdapter implements AuditPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public AuditPublisherAdapter(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void registrar(AuditEvento evento) {
        eventPublisher.publishEvent(Objects.requireNonNull(evento, "evento de auditoria não pode ser nulo"));
    }
}
