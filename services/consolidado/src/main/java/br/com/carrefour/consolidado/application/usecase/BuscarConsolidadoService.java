package br.com.carrefour.consolidado.application.usecase;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.port.in.BuscarConsolidadoUseCase;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class BuscarConsolidadoService implements BuscarConsolidadoUseCase {

    private final SaldoConsolidadoRepository repository;

    public BuscarConsolidadoService(SaldoConsolidadoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Cacheable(value = "saldo-consolidado", key = "#data")
    @Transactional(readOnly = true)
    public Optional<SaldoConsolidado> executar(LocalDate data) {
        return repository.buscarPorData(data);
    }
}
