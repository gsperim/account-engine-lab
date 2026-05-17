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

        log.info("outbox_relay pendentes={}", pendentes.size());

        for (var entry : pendentes) {
            try {
                publisher.publicar(entry.getPayload(), entry.getLancamentoId());
                entry.marcarPublicado();
                outboxRepo.save(entry);
                log.info("outbox_publicado outbox_id={} lancamento_id={} tentativas={}",
                        entry.getId(), entry.getLancamentoId(), entry.getTentativas() + 1);
            } catch (Exception e) {
                entry.incrementarTentativas();
                outboxRepo.save(entry);
                log.warn("outbox_falhou outbox_id={} lancamento_id={} tentativas={} motivo={}",
                        entry.getId(), entry.getLancamentoId(), entry.getTentativas(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void limparPublicados() {
        var limite = LocalDateTime.now().minusDays(7);
        int removidos = outboxRepo.deletarPublicadosAntes(limite);
        if (removidos > 0) {
            log.info("outbox_cleanup removidos={} antes={}", removidos, limite);
        }
    }

}
