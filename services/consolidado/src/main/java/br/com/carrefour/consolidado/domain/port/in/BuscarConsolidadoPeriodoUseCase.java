package br.com.carrefour.consolidado.domain.port.in;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;

import java.time.LocalDate;
import java.util.List;

public interface BuscarConsolidadoPeriodoUseCase {
    List<SaldoConsolidado> executar(LocalDate inicio, LocalDate fim);
}
