package br.com.carrefour.lancamentos.domain.exception;

public class LancamentoConflitanteException extends RuntimeException {

    public LancamentoConflitanteException(String idempotencyKey) {
        super("Idempotency-Key '" + idempotencyKey + "' já foi usada com payload diferente");
    }
}
