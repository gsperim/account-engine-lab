package br.com.carrefour.lancamentos.domain.port.out;

import br.com.carrefour.lancamentos.domain.model.Lancamento;

public interface OutboxPort {
    void registrar(Lancamento lancamento);
}
