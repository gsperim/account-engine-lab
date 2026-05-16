package br.com.carrefour.lancamentos.domain.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ResumoDiarioUseCase {

    record Query(LocalDate data) {}

    record Resultado(
            LocalDate data,
            BigDecimal totalCreditos,
            BigDecimal totalDebitos,
            long totalLancamentos) {}

    Resultado executar(Query query);
}
