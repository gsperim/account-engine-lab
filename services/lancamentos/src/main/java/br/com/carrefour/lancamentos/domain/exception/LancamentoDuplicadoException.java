package br.com.carrefour.lancamentos.domain.exception;

public class LancamentoDuplicadoException extends RuntimeException {
    public LancamentoDuplicadoException(String idempotencyKey) {
        super("Lançamento já registrado para a chave: " + idempotencyKey);
    }
}
