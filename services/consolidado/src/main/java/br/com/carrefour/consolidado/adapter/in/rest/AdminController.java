package br.com.carrefour.consolidado.adapter.in.rest;

import br.com.carrefour.consolidado.adapter.in.rest.dto.generated.ReconstrucaoResponse;
import br.com.carrefour.consolidado.adapter.in.rest.generated.AdminApi;
import br.com.carrefour.consolidado.domain.port.in.ReconstruirConsolidadoUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class AdminController implements AdminApi {

    private final ReconstruirConsolidadoUseCase reconstruirUseCase;

    public AdminController(ReconstruirConsolidadoUseCase reconstruirUseCase) {
        this.reconstruirUseCase = reconstruirUseCase;
    }

    @Override
    public ResponseEntity<ReconstrucaoResponse> reconstruirConsolidado(LocalDate dataInicio, LocalDate dataFim) {
        var resultado = reconstruirUseCase.executar(
                new ReconstruirConsolidadoUseCase.Command(dataInicio, dataFim));
        return ResponseEntity.ok(new ReconstrucaoResponse()
                .datasProcessadas(resultado.datasProcessadas())
                .divergenciasCorrigidas(resultado.divergenciasCorrigidas())
                .dataInicio(resultado.dataInicio())
                .dataFim(resultado.dataFim()));
    }
}
