package br.com.carrefour.lancamentos.adapter.out.messaging;

import br.com.carrefour.lancamentos.adapter.out.persistence.OutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxJpaRepository outboxRepo;
    private final OutboxPublisher     publisher;

    public OutboxRelay(OutboxJpaRepository outboxRepo, OutboxPublisher publisher) {
        this.outboxRepo = outboxRepo;
        this.publisher  = publisher;
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 15_000)
    @Transactional
    public void processar() {
        var pendentes = outboxRepo.buscarPendentes();
        if (pendentes.isEmpty()) return;

        log.atInfo()
                .addKeyValue("event",    "outbox_relay_inicio")
                .addKeyValue("pendentes", pendentes.size())
                .log("Outbox relay iniciado");

        for (var entry : pendentes) {
            try {
                publisher.publicar(entry.getPayload(), entry.getLancamentoId());
                entry.marcarPublicado();
                outboxRepo.save(entry);
                log.atInfo()
                        .addKeyValue("event",        "outbox_publicado")
                        .addKeyValue("outbox_id",    entry.getId())
                        .addKeyValue("lancamento_id", entry.getLancamentoId())
                        .addKeyValue("tentativas",   entry.getTentativas() + 1)
                        .log("Evento publicado no RabbitMQ");
            } catch (Exception e) {
                entry.incrementarTentativas();
                outboxRepo.save(entry);
                log.atWarn()
                        .addKeyValue("event",        "outbox_falhou")
                        .addKeyValue("outbox_id",    entry.getId())
                        .addKeyValue("lancamento_id", entry.getLancamentoId())
                        .addKeyValue("tentativas",   entry.getTentativas())
                        .setCause(e)
                        .log("Falha ao publicar evento — será retentado");
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void limparPublicados() {
        var limite    = LocalDateTime.now().minusDays(7);
        int removidos = outboxRepo.deletarPublicadosAntes(limite);
        if (removidos > 0) {
            log.atInfo()
                    .addKeyValue("event",    "outbox_cleanup")
                    .addKeyValue("removidos", removidos)
                    .addKeyValue("antes",    limite)
                    .log("Outbox cleanup concluído");
        }
    }
}
