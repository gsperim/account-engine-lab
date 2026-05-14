package br.com.carrefour.lancamentos.domain.model;

import java.util.UUID;

public final class LancamentoId {

    private final UUID value;

    private LancamentoId(UUID value) {
        this.value = value;
    }

    public static LancamentoId novo() {
        return new LancamentoId(UUID.randomUUID());
    }

    public static LancamentoId de(UUID uuid) {
        if (uuid == null) throw new IllegalArgumentException("ID não pode ser nulo");
        return new LancamentoId(uuid);
    }

    public static LancamentoId de(String uuid) {
        try {
            return de(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Formato de ID inválido: " + uuid);
        }
    }

    public UUID toUUID() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LancamentoId id)) return false;
        return value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
