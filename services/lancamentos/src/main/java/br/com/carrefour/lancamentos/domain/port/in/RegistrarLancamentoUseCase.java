package br.com.carrefour.lancamentos.domain.port.in;

import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.model.Valor;

import java.time.LocalDate;

public interface RegistrarLancamentoUseCase {

    record Command(
        TipoLancamento tipo,
        Valor valor,
        String descricao,
        LocalDate dataCompetencia,
        String operadorId,
        String idempotencyKey
    ) {}

    Lancamento executar(Command command);
}
