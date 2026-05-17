package br.com.carrefour.lancamentos.application.usecase;

import br.com.carrefour.lancamentos.domain.exception.LancamentoJaEstornadoException;
import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.port.in.EstornarLancamentoUseCase;
import br.com.carrefour.lancamentos.domain.port.out.LancamentoRepository;
import br.com.carrefour.lancamentos.domain.port.out.OutboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EstornarLancamentoService implements EstornarLancamentoUseCase {

    private static final Logger log = LoggerFactory.getLogger(EstornarLancamentoService.class);

    private final LancamentoRepository repository;
    private final OutboxPort           outbox;

    public EstornarLancamentoService(LancamentoRepository repository, OutboxPort outbox) {
        this.repository = repository;
        this.outbox     = outbox;
    }

    @Override
    @Transactional
    public Lancamento executar(Command command) {
        var original = repository.buscarPorIdComLock(command.originalId())
                .orElseThrow(() -> new NoSuchElementException("Lançamento não encontrado: " + command.originalId()));

        if (original.isEstornado()) {
            throw new LancamentoJaEstornadoException(command.originalId().toUUID());
        }

        // ID derivado deterministicamente — garante idempotência sem Idempotency-Key extra
        var estornoId = LancamentoId.de(UUID.nameUUIDFromBytes(
                ("estorno-" + command.originalId().toUUID()).getBytes(StandardCharsets.UTF_8)));

        // Verifica se o estorno já foi criado (replay idempotente)
        var estornoExistente = repository.buscarPorId(estornoId);
        if (estornoExistente.isPresent()) {
            log.info("estorno idempotente replay original_id={}", command.originalId().toUUID());
            return estornoExistente.get();
        }

        var tipoEstorno = original.getTipo() == TipoLancamento.CREDITO
                ? TipoLancamento.DEBITO
                : TipoLancamento.CREDITO;

        var estorno = Lancamento.criar(
                estornoId,
                tipoEstorno,
                original.getValor(),
                "Estorno de " + command.originalId().toUUID(),
                original.getDataCompetencia(),
                command.operadorId()
        );

        original.marcarEstornado();
        repository.salvar(original);

        var salvo = repository.salvar(estorno);
        outbox.registrar(salvo);

        log.info("estorno registrado original_id={} estorno_id={}",
                command.originalId().toUUID(), estornoId.toUUID());

        return salvo;
    }
}
