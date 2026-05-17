package br.com.carrefour.lancamentos.application.usecase;

import br.com.carrefour.lancamentos.domain.exception.LancamentoConflitanteException;
import br.com.carrefour.lancamentos.domain.model.AuditEvento;
import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.PayloadHash;
import br.com.carrefour.lancamentos.domain.port.in.RegistrarLancamentoUseCase;
import br.com.carrefour.lancamentos.domain.port.out.AuditPublisher;
import br.com.carrefour.lancamentos.domain.port.out.LancamentoRepository;
import br.com.carrefour.lancamentos.domain.port.out.OutboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class RegistrarLancamentoService implements RegistrarLancamentoUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarLancamentoService.class);

    private final LancamentoRepository repository;
    private final OutboxPort           outbox;
    private final AuditPublisher       audit;

    public RegistrarLancamentoService(LancamentoRepository repository, OutboxPort outbox,
                                      AuditPublisher audit) {
        this.repository = repository;
        this.outbox     = outbox;
        this.audit      = audit;
    }

    @Override
    @Transactional
    public Lancamento executar(Command command) {
        var id = LancamentoId.de(command.idempotencyKey());

        var existente = repository.buscarPorId(id);
        if (existente.isPresent()) {
            var hashRecebido = PayloadHash.compute(command.tipo(), command.valor(),
                    command.dataCompetencia(), command.descricao());
            if (existente.get().getPayloadHash().equals(hashRecebido)) {
                log.atInfo()
                        .addKeyValue("event",           "lancamento_replay_idempotente")
                        .addKeyValue("idempotency_key", command.idempotencyKey())
                        .log("Replay idempotente — lançamento já existe com mesmo payload");
                return existente.get();
            }
            log.atWarn()
                    .addKeyValue("event",           "lancamento_conflitante")
                    .addKeyValue("idempotency_key", command.idempotencyKey())
                    .log("Conflito de idempotência — mesmo key, payload diferente");
            throw new LancamentoConflitanteException(command.idempotencyKey());
        }

        var lancamento = Lancamento.criar(
                id, command.tipo(), command.valor(),
                command.descricao(), command.dataCompetencia(), command.operadorId());

        var salvo = repository.salvar(lancamento);
        outbox.registrar(salvo);
        audit.registrar(new AuditEvento(
                command.operadorId(),
                "lancamento.registrado",
                salvo.getId().toUUID(),
                Map.of("tipo", command.tipo().name(), "valor", command.valor().toString())
        ));

        log.atInfo()
                .addKeyValue("event",           "lancamento_registrado")
                .addKeyValue("idempotency_key", command.idempotencyKey())
                .addKeyValue("tipo",            command.tipo())
                .addKeyValue("operador_id",     command.operadorId())
                .log("Lançamento registrado com sucesso");

        return salvo;
    }
}
