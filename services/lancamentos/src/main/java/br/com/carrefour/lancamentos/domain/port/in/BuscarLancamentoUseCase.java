package br.com.carrefour.lancamentos.domain.port.in;

import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;

import java.util.Optional;

public interface BuscarLancamentoUseCase {
    Optional<Lancamento> executar(LancamentoId id);
}
