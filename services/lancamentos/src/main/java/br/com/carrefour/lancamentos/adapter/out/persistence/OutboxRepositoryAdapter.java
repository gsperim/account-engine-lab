package br.com.carrefour.lancamentos.adapter.out.persistence;

import br.com.carrefour.lancamentos.adapter.out.messaging.LancamentoRegistradoEvento;
import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.port.out.OutboxPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class OutboxRepositoryAdapter implements OutboxPort {

    private final OutboxJpaRepository repo;
    private final ObjectMapper mapper;

    public OutboxRepositoryAdapter(OutboxJpaRepository repo, ObjectMapper mapper) {
        this.repo   = repo;
        this.mapper = mapper;
    }

    @Override
    public void registrar(Lancamento lancamento) {
        var evento = new LancamentoRegistradoEvento(
                UUID.randomUUID(),
                "LancamentoRegistrado",
                "1.0",
                lancamento.getCriadoEm().atOffset(ZoneOffset.UTC),
                new LancamentoRegistradoEvento.Payload(
                        lancamento.getId().toUUID(),
                        lancamento.getTipo().name(),
                        lancamento.getValor().toBigDecimal(),
                        lancamento.getDataCompetencia(),
                        lancamento.getOperadorId(),
                        lancamento.getCriadoEm().atOffset(ZoneOffset.UTC)
                )
        );

        try {
            var payload = mapper.writeValueAsString(evento);
            repo.save(new OutboxJpaEntity(
                    evento.id(),
                    lancamento.getId().toUUID(),
                    evento.tipo(),
                    payload
            ));
        } catch (Exception e) {
            throw new RuntimeException("Falha ao serializar evento para outbox", e);
        }
    }
}
