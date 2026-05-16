package br.com.carrefour.consolidado.domain.port.in;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;

import java.time.LocalDate;
import java.util.Optional;

public interface BuscarConsolidadoUseCase {
    Optional<SaldoConsolidado> executar(LocalDate data);
}
