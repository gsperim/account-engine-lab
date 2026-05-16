package br.com.carrefour.consolidado.domain.port.out;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SaldoConsolidadoRepository {
    Optional<SaldoConsolidado> buscarPorData(LocalDate data);
    List<SaldoConsolidado> buscarPorPeriodo(LocalDate inicio, LocalDate fim);
    SaldoConsolidado salvar(SaldoConsolidado saldo);
}
