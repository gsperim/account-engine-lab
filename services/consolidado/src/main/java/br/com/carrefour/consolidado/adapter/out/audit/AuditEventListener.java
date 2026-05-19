package br.com.carrefour.consolidado.adapter.out.audit;

import br.com.carrefour.consolidado.adapter.out.persistence.AuditLogJpaEntity;
import br.com.carrefour.consolidado.adapter.out.persistence.AuditLogJpaRepository;
import br.com.carrefour.consolidado.domain.model.AuditEvento;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditLogJpaRepository repository;
    private final ObjectMapper          mapper;

    public AuditEventListener(AuditLogJpaRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper     = mapper;
    }

    // Dispara somente após o commit da transação principal — não bloqueia o response.
    // @Async garante thread separada; com Virtual Threads habilitado, usa virtual thread.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handle(AuditEvento evento) {
        try {
            var contextoJson = mapper.writeValueAsString(evento.contexto());
            repository.save(new AuditLogJpaEntity(
                    UUID.randomUUID(),
                    evento.operadorId(),
                    evento.acao(),
                    evento.recursoId(),
                    contextoJson
            ));
        } catch (JsonProcessingException | RuntimeException e) {
            log.atError()
                    .addKeyValue("event",      "audit_falha")
                    .addKeyValue("acao",       evento.acao())
                    .addKeyValue("recurso_id", evento.recursoId())
                    .setCause(e)
                    .log("Falha ao persistir registro de auditoria");
        }
    }
}
