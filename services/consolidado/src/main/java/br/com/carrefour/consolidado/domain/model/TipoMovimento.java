package br.com.carrefour.consolidado.domain.model;

public enum TipoMovimento {
    DEBITO, CREDITO;

    public static TipoMovimento de(String valor) {
        try {
            return valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de movimento inválido: " + valor);
        }
    }
}
