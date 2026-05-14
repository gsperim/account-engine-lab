package br.com.carrefour.lancamentos.application.usecase;

import br.com.carrefour.lancamentos.domain.exception.LancamentoDuplicadoException;
import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.port.in.RegistrarLancamentoUseCase;
import br.com.carrefour.lancamentos.domain.port.out.LancamentoRepository;
import br.com.carrefour.lancamentos.domain.port.out.OutboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrarLancamentoService implements RegistrarLancamentoUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarLancamentoService.class);

    private final LancamentoRepository repository;
    private final OutboxPort           outbox;

    public RegistrarLancamentoService(LancamentoRepository repository, OutboxPort outbox) {
        this.repository = repository;
        this.outbox     = outbox;
    }

    @Override
    @Transactional
    public Lancamento executar(Command command) {
        var id = LancamentoId.de(command.idempotencyKey());

        log.info("registrando lancamento idempotency_key={} tipo={} valor={}",
                command.idempotencyKey(), command.tipo(), command.valor().toBigDecimal());

        if (repository.existePorId(id)) {
            log.warn("lancamento duplicado idempotency_key={}", command.idempotencyKey());
            throw new LancamentoDuplicadoException(command.idempotencyKey());
        }

        var lancamento = Lancamento.criar(
            id,
            command.tipo(),
            command.valor(),
            command.descricao(),
            command.dataCompetencia(),
            command.operadorId()
        );

        var salvo = repository.salvar(lancamento);
        outbox.registrar(salvo);  // atômico na mesma transação — Rabbit pode cair à vontade

        log.info("lancamento registrado idempotency_key={} operador={}",
                command.idempotencyKey(), command.operadorId());

        return salvo;
    }
}
