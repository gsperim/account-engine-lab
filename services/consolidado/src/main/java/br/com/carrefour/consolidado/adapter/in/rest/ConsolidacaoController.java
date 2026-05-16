package br.com.carrefour.consolidado.adapter.in.rest;

import br.com.carrefour.consolidado.adapter.in.rest.dto.generated.SaldoConsolidadoResponse;
import br.com.carrefour.consolidado.adapter.in.rest.generated.ConsolidacaoApi;
import br.com.carrefour.consolidado.domain.port.in.BuscarConsolidadoPeriodoUseCase;
import br.com.carrefour.consolidado.domain.port.in.BuscarConsolidadoUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class ConsolidacaoController implements ConsolidacaoApi {

    private final BuscarConsolidadoUseCase buscarUseCase;
    private final BuscarConsolidadoPeriodoUseCase buscarPeriodoUseCase;
    private final ConsolidacaoMapper mapper;

    public ConsolidacaoController(BuscarConsolidadoUseCase buscarUseCase,
                                   BuscarConsolidadoPeriodoUseCase buscarPeriodoUseCase,
                                   ConsolidacaoMapper mapper) {
        this.buscarUseCase       = buscarUseCase;
        this.buscarPeriodoUseCase = buscarPeriodoUseCase;
        this.mapper              = mapper;
    }

    @Override
    public ResponseEntity<SaldoConsolidadoResponse> consultarSaldoDiario(LocalDate data) {
        return buscarUseCase.executar(data)
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<List<SaldoConsolidadoResponse>> listarSaldosPorPeriodo(LocalDate dataInicio, LocalDate dataFim) {
        var saldos = buscarPeriodoUseCase.executar(dataInicio, dataFim).stream()
                .map(mapper::toResponse).toList();
        return ResponseEntity.ok(saldos);
    }
}
