package br.com.carrefour.consolidado.adapter.in.rest;

import br.com.carrefour.consolidado.adapter.in.rest.dto.generated.ReconstrucaoResponse;
import br.com.carrefour.consolidado.adapter.in.rest.generated.AdminApi;
import br.com.carrefour.consolidado.domain.port.in.ReconstruirConsolidadoUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
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
                new ReconstruirConsolidadoUseCase.Command(dataInicio, dataFim, extrairOperadorId()));
        return ResponseEntity.ok(new ReconstrucaoResponse()
                .datasProcessadas(resultado.datasProcessadas())
                .divergenciasCorrigidas(resultado.divergenciasCorrigidas())
                .dataInicio(resultado.dataInicio())
                .dataFim(resultado.dataFim()));
    }

    private String extrairOperadorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AbstractOAuth2TokenAuthenticationToken<?> token
                && token.getToken() instanceof Jwt jwt) {
            var sub = jwt.getSubject();
            return (sub != null && !sub.isBlank()) ? sub : jwt.getClaimAsString("preferred_username");
        }
        return "system";
    }
}
