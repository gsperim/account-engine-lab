package br.com.carrefour.lancamentos.domain.exception;

import java.util.UUID;

public class LancamentoJaEstornadoException extends RuntimeException {

    public LancamentoJaEstornadoException(UUID id) {
        super("Lançamento " + id + " já foi estornado");
    }
}
