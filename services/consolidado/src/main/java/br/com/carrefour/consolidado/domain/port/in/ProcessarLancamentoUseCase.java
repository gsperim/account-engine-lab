package br.com.carrefour.consolidado.domain.port.in;

import br.com.carrefour.consolidado.domain.model.TipoMovimento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface ProcessarLancamentoUseCase {

    record Command(UUID lancamentoId, TipoMovimento tipo, BigDecimal valor, LocalDate dataCompetencia) {}

    void executar(Command command);
}
