package br.com.carrefour.consolidado.domain.port.in;

import java.time.LocalDate;

public interface ReconstruirConsolidadoUseCase {

    record Command(LocalDate dataInicio, LocalDate dataFim) {}

    record Resultado(int datasProcessadas, int divergenciasCorrigidas, LocalDate dataInicio, LocalDate dataFim) {}

    Resultado executar(Command command);
}
