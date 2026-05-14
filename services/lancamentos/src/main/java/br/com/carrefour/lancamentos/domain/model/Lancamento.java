package br.com.carrefour.lancamentos.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Lancamento {

    private final LancamentoId id;
    private final TipoLancamento tipo;
    private final Valor valor;
    private final String descricao;
    private final LocalDate dataCompetencia;
    private final String operadorId;
    private final LocalDateTime criadoEm;

    private Lancamento(Builder builder) {
        this.id              = builder.id;
        this.tipo            = builder.tipo;
        this.valor           = builder.valor;
        this.descricao       = builder.descricao;
        this.dataCompetencia = builder.dataCompetencia;
        this.operadorId      = builder.operadorId;
        this.criadoEm        = builder.criadoEm;
    }

    public static Lancamento criar(
            LancamentoId id,
            TipoLancamento tipo,
            Valor valor,
            String descricao,
            LocalDate dataCompetencia,
            String operadorId) {

        if (id == null)              throw new IllegalArgumentException("Id é obrigatório");
        if (tipo == null)            throw new IllegalArgumentException("Tipo é obrigatório");
        if (valor == null)           throw new IllegalArgumentException("Valor é obrigatório");
        if (dataCompetencia == null) throw new IllegalArgumentException("Data de competência é obrigatória");
        if (operadorId == null || operadorId.isBlank()) throw new IllegalArgumentException("OperadorId é obrigatório");

        return new Builder()
                .id(id)
                .tipo(tipo)
                .valor(valor)
                .descricao(descricao)
                .dataCompetencia(dataCompetencia)
                .operadorId(operadorId)
                .criadoEm(LocalDateTime.now())
                .build();
    }

    // Reconstitui aggregate a partir da persistência
    public static Lancamento reconstituir(
            LancamentoId id,
            TipoLancamento tipo,
            Valor valor,
            String descricao,
            LocalDate dataCompetencia,
            String operadorId,
            LocalDateTime criadoEm) {

        return new Builder()
                .id(id)
                .tipo(tipo)
                .valor(valor)
                .descricao(descricao)
                .dataCompetencia(dataCompetencia)
                .operadorId(operadorId)
                .criadoEm(criadoEm)
                .build();
    }

    public LancamentoId getId()              { return id; }
    public TipoLancamento getTipo()          { return tipo; }
    public Valor getValor()                  { return valor; }
    public String getDescricao()             { return descricao; }
    public LocalDate getDataCompetencia()    { return dataCompetencia; }
    public String getOperadorId()            { return operadorId; }
    public LocalDateTime getCriadoEm()       { return criadoEm; }

    private static class Builder {
        LancamentoId id;
        TipoLancamento tipo;
        Valor valor;
        String descricao;
        LocalDate dataCompetencia;
        String operadorId;
        LocalDateTime criadoEm;

        Builder id(LancamentoId id)                    { this.id = id; return this; }
        Builder tipo(TipoLancamento tipo)              { this.tipo = tipo; return this; }
        Builder valor(Valor valor)                     { this.valor = valor; return this; }
        Builder descricao(String descricao)            { this.descricao = descricao; return this; }
        Builder dataCompetencia(LocalDate d)           { this.dataCompetencia = d; return this; }
        Builder operadorId(String operadorId)          { this.operadorId = operadorId; return this; }
        Builder criadoEm(LocalDateTime criadoEm)       { this.criadoEm = criadoEm; return this; }
        Lancamento build()                             { return new Lancamento(this); }
    }
}
