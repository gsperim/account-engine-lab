package br.com.carrefour.consolidado.application.usecase;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.model.TipoMovimento;
import br.com.carrefour.consolidado.domain.port.in.ProcessarLancamentoUseCase;
import br.com.carrefour.consolidado.domain.port.out.LancamentosAplicadosRepository;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessarLancamentoService implements ProcessarLancamentoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarLancamentoService.class);

    private final SaldoConsolidadoRepository repository;
    private final LancamentosAplicadosRepository aplicados;

    public ProcessarLancamentoService(SaldoConsolidadoRepository repository,
                                      LancamentosAplicadosRepository aplicados) {
        this.repository = repository;
        this.aplicados  = aplicados;
    }

    @Override
    @CacheEvict(value = "saldo-consolidado", key = "#command.dataCompetencia()")
    @Transactional
    public void executar(Command command) {
        if (aplicados.existePorId(command.lancamentoId())) {
            log.atInfo()
                    .addKeyValue("event",         "lancamento_ja_aplicado")
                    .addKeyValue("lancamento_id", command.lancamentoId())
                    .log("Evento ignorado — já aplicado ao saldo (at-least-once redelivery)");
            return;
        }

        var saldo = repository.buscarPorData(command.dataCompetencia())
                .orElseGet(() -> SaldoConsolidado.novo(command.dataCompetencia()));

        if (command.tipo() == TipoMovimento.CREDITO) {
            saldo.aplicarCredito(command.valor());
        } else {
            saldo.aplicarDebito(command.valor());
        }

        repository.salvar(saldo);
        aplicados.registrar(command.lancamentoId(), command.dataCompetencia());
    }
}
