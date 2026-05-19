package br.com.carrefour.consolidado.application.usecase;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// Bean separado para que @Transactional(REQUIRES_NEW) seja aplicado via Spring AOP proxy.
// Self-invocation em ReconstruirConsolidadoService bypassaria o proxy — por isso a extração.
@Component
class ReconstruirPorDataHelper {

    private final SaldoConsolidadoRepository repository;

    ReconstruirPorDataHelper(SaldoConsolidadoRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "saldo-consolidado", key = "#data")
    public void salvarEEvictCache(LocalDate data, SaldoConsolidado saldo) {
        repository.salvar(saldo);
    }
}
