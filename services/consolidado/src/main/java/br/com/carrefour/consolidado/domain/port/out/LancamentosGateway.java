package br.com.carrefour.consolidado.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface LancamentosGateway {

    record ResumoDiario(
            LocalDate data,
            BigDecimal totalCreditos,
            BigDecimal totalDebitos,
            long totalLancamentos) {}

    ResumoDiario buscarResumoDiario(LocalDate data);
}
