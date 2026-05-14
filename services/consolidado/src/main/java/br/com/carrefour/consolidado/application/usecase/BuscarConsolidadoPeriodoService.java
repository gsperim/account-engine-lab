package br.com.carrefour.consolidado.application.usecase;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.port.in.BuscarConsolidadoPeriodoUseCase;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BuscarConsolidadoPeriodoService implements BuscarConsolidadoPeriodoUseCase {

    private static final int PERIODO_MAXIMO_DIAS = 90;

    private final SaldoConsolidadoRepository repository;

    public BuscarConsolidadoPeriodoService(SaldoConsolidadoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaldoConsolidado> executar(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("data_fim não pode ser anterior a data_inicio");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) > PERIODO_MAXIMO_DIAS) {
            throw new IllegalArgumentException("Período máximo permitido é de 90 dias");
        }
        return repository.buscarPorPeriodo(inicio, fim);
    }
}
