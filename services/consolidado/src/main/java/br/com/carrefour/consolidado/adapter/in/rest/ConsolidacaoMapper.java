package br.com.carrefour.consolidado.adapter.in.rest;

import br.com.carrefour.consolidado.adapter.in.rest.dto.generated.SaldoConsolidadoResponse;
import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
public class ConsolidacaoMapper {

    public SaldoConsolidadoResponse toResponse(SaldoConsolidado s) {
        return new SaldoConsolidadoResponse()
                .data(s.getData())
                .totalCreditos(s.getTotalCreditos().doubleValue())
                .totalDebitos(s.getTotalDebitos().doubleValue())
                .saldo(s.getSaldo().doubleValue())
                .totalLancamentos(s.getTotalLancamentos())
                .ultimaAtualizacao(s.getUltimaAtualizacao().atOffset(ZoneOffset.UTC));
    }
}
