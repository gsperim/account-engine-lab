package br.com.carrefour.lancamentos.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Valor {

    private final BigDecimal amount;

    private Valor(BigDecimal amount) {
        this.amount = amount;
    }

    public static Valor de(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Valor não pode ser nulo");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Valor deve ser positivo");
        return new Valor(amount.setScale(2, RoundingMode.UNNECESSARY));
    }

    public static Valor de(String amount) {
        try {
            return de(new BigDecimal(amount));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Formato de valor inválido: " + amount);
        }
    }

    public Valor somar(Valor outro) {
        return new Valor(this.amount.add(outro.amount));
    }

    public Valor subtrair(Valor outro) {
        return new Valor(this.amount.subtract(outro.amount));
    }

    public BigDecimal toBigDecimal() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Valor v)) return false;
        return amount.compareTo(v.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
