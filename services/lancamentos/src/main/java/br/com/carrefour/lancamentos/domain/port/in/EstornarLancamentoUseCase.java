package br.com.carrefour.lancamentos.domain.port.in;

import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;

public interface EstornarLancamentoUseCase {

    record Command(LancamentoId originalId, String operadorId) {}

    Lancamento executar(Command command);
}
