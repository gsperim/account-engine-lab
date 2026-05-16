package br.com.carrefour.lancamentos.adapter.out.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LancamentoRegistradoEvento(
        UUID id,
        String tipo,
        String versao,
        @JsonProperty("criado_em") OffsetDateTime criadoEm,
        Payload payload
) {
    public record Payload(
            @JsonProperty("lancamento_id") UUID lancamentoId,
            String tipo,
            BigDecimal valor,
            @JsonProperty("data_competencia") LocalDate dataCompetencia,
            @JsonProperty("operador_id") String operadorId,
            @JsonProperty("criado_em") OffsetDateTime criadoEm
    ) {}
}
