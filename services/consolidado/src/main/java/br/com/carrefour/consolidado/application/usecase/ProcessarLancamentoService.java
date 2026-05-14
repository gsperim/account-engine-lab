package br.com.carrefour.consolidado.application.usecase;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.model.TipoMovimento;
import br.com.carrefour.consolidado.domain.port.in.ProcessarLancamentoUseCase;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessarLancamentoService implements ProcessarLancamentoUseCase {

    private final SaldoConsolidadoRepository repository;

    public ProcessarLancamentoService(SaldoConsolidadoRepository repository) {
        this.repository = repository;
    }

    @Override
    @CacheEvict(value = "saldo-consolidado", key = "#command.dataCompetencia()")
    @Transactional
    public void executar(Command command) {
        var saldo = repository.buscarPorData(command.dataCompetencia())
                .orElseGet(() -> SaldoConsolidado.novo(command.dataCompetencia()));

        if (command.tipo() == TipoMovimento.CREDITO) {
            saldo.aplicarCredito(command.valor());
        } else {
            saldo.aplicarDebito(command.valor());
        }

        repository.salvar(saldo);
    }
}
