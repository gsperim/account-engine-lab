package br.com.carrefour.consolidado.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SaldoConsolidado {

    private final LocalDate data;
    private BigDecimal totalCreditos;
    private BigDecimal totalDebitos;
    private int totalLancamentos;
    private LocalDateTime ultimaAtualizacao;

    private SaldoConsolidado(LocalDate data, BigDecimal totalCreditos,
                              BigDecimal totalDebitos, int totalLancamentos,
                              LocalDateTime ultimaAtualizacao) {
        this.data              = data;
        this.totalCreditos     = totalCreditos;
        this.totalDebitos      = totalDebitos;
        this.totalLancamentos  = totalLancamentos;
        this.ultimaAtualizacao = ultimaAtualizacao;
    }

    public static SaldoConsolidado novo(LocalDate data) {
        if (data == null) throw new IllegalArgumentException("Data é obrigatória");
        return new SaldoConsolidado(data, BigDecimal.ZERO, BigDecimal.ZERO, 0, LocalDateTime.now());
    }

    @JsonCreator
    public static SaldoConsolidado reconstituir(
            @JsonProperty("data")              LocalDate data,
            @JsonProperty("totalCreditos")     BigDecimal totalCreditos,
            @JsonProperty("totalDebitos")      BigDecimal totalDebitos,
            @JsonProperty("totalLancamentos")  int totalLancamentos,
            @JsonProperty("ultimaAtualizacao") LocalDateTime ultimaAtualizacao) {
        return new SaldoConsolidado(data, totalCreditos, totalDebitos, totalLancamentos, ultimaAtualizacao);
    }

    public void aplicarCredito(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Valor do crédito deve ser positivo");
        totalCreditos     = totalCreditos.add(valor);
        totalLancamentos  = totalLancamentos + 1;
        ultimaAtualizacao = LocalDateTime.now();
    }

    public void aplicarDebito(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Valor do débito deve ser positivo");
        totalDebitos      = totalDebitos.add(valor);
        totalLancamentos  = totalLancamentos + 1;
        ultimaAtualizacao = LocalDateTime.now();
    }

    public BigDecimal getSaldo() {
        return totalCreditos.subtract(totalDebitos);
    }

    public LocalDate getData()                   { return data; }
    public BigDecimal getTotalCreditos()         { return totalCreditos; }
    public BigDecimal getTotalDebitos()          { return totalDebitos; }
    public int getTotalLancamentos()             { return totalLancamentos; }
    public LocalDateTime getUltimaAtualizacao()  { return ultimaAtualizacao; }
}
