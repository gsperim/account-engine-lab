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
    private final String payloadHash;
    private boolean estornado;
    private LocalDateTime estornadoEm;

    private Lancamento(Builder builder) {
        this.id              = builder.id;
        this.tipo            = builder.tipo;
        this.valor           = builder.valor;
        this.descricao       = builder.descricao;
        this.dataCompetencia = builder.dataCompetencia;
        this.operadorId      = builder.operadorId;
        this.criadoEm        = builder.criadoEm;
        this.payloadHash     = builder.payloadHash;
        this.estornado       = builder.estornado;
        this.estornadoEm     = builder.estornadoEm;
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
                .payloadHash(PayloadHash.compute(tipo, valor, dataCompetencia, descricao))
                .estornado(false)
                .build();
    }

    public static Lancamento reconstituir(
            LancamentoId id,
            TipoLancamento tipo,
            Valor valor,
            String descricao,
            LocalDate dataCompetencia,
            String operadorId,
            LocalDateTime criadoEm,
            String payloadHash,
            boolean estornado,
            LocalDateTime estornadoEm) {

        return new Builder()
                .id(id)
                .tipo(tipo)
                .valor(valor)
                .descricao(descricao)
                .dataCompetencia(dataCompetencia)
                .operadorId(operadorId)
                .criadoEm(criadoEm)
                .payloadHash(payloadHash)
                .estornado(estornado)
                .estornadoEm(estornadoEm)
                .build();
    }

    public void marcarEstornado() {
        this.estornado   = true;
        this.estornadoEm = LocalDateTime.now();
    }

    public LancamentoId getId()              { return id; }
    public TipoLancamento getTipo()          { return tipo; }
    public Valor getValor()                  { return valor; }
    public String getDescricao()             { return descricao; }
    public LocalDate getDataCompetencia()    { return dataCompetencia; }
    public String getOperadorId()            { return operadorId; }
    public LocalDateTime getCriadoEm()       { return criadoEm; }
    public String getPayloadHash()           { return payloadHash; }
    public boolean isEstornado()             { return estornado; }
    public LocalDateTime getEstornadoEm()    { return estornadoEm; }

    private static class Builder {
        LancamentoId id;
        TipoLancamento tipo;
        Valor valor;
        String descricao;
        LocalDate dataCompetencia;
        String operadorId;
        LocalDateTime criadoEm;
        String payloadHash;
        boolean estornado;
        LocalDateTime estornadoEm;

        Builder id(LancamentoId id)                    { this.id = id; return this; }
        Builder tipo(TipoLancamento tipo)              { this.tipo = tipo; return this; }
        Builder valor(Valor valor)                     { this.valor = valor; return this; }
        Builder descricao(String descricao)            { this.descricao = descricao; return this; }
        Builder dataCompetencia(LocalDate d)           { this.dataCompetencia = d; return this; }
        Builder operadorId(String operadorId)          { this.operadorId = operadorId; return this; }
        Builder criadoEm(LocalDateTime criadoEm)       { this.criadoEm = criadoEm; return this; }
        Builder payloadHash(String payloadHash)        { this.payloadHash = payloadHash; return this; }
        Builder estornado(boolean estornado)           { this.estornado = estornado; return this; }
        Builder estornadoEm(LocalDateTime estornadoEm) { this.estornadoEm = estornadoEm; return this; }
        Lancamento build()                             { return new Lancamento(this); }
    }
}
