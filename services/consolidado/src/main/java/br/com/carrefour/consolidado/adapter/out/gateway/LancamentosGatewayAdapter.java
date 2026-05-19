package br.com.carrefour.consolidado.adapter.out.gateway;

import br.com.carrefour.consolidado.NaoTestablePorDesign;
import br.com.carrefour.consolidado.domain.port.out.LancamentosGateway;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

// Em produção: substituir token estático por Client Credentials do Keycloak
// (spring-security-oauth2-client + ClientRegistration).
@Component
@NaoTestablePorDesign(motivo = "RestClient configurado com baseUrl de variável de ambiente. Teste requer WireMock ou @RestClientTest com MockRestServiceServer.")
public class LancamentosGatewayAdapter implements LancamentosGateway {

    private static final Logger log = LoggerFactory.getLogger(LancamentosGatewayAdapter.class);

    private final RestClient restClient;

    public LancamentosGatewayAdapter(
            RestClient.Builder builder,
            @Value("${reconciliacao.lancamentos.base-url}") String baseUrl,
            @Value("${reconciliacao.lancamentos.service-token}") String serviceToken) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + serviceToken)
                .build();
    }

    @Override
    @Retry(name = "lancamentos-gateway")
    @CircuitBreaker(name = "lancamentos-gateway", fallbackMethod = "buscarResumoDiarioFallback")
    public ResumoDiario buscarResumoDiario(LocalDate data) {
        var response = restClient.get()
                .uri("/registros/resumo?data={data}", data)
                .retrieve()
                .body(ResumoDiarioDto.class);
        if (response == null) throw new IllegalStateException("Resposta vazia do serviço de lançamentos");
        return new ResumoDiario(data, response.totalCreditos(), response.totalDebitos(), response.totalLancamentos());
    }

    ResumoDiario buscarResumoDiarioFallback(LocalDate data, Throwable t) {
        log.atWarn()
                .addKeyValue("event", "gateway_lancamentos_indisponivel")
                .addKeyValue("data", data)
                .setCause(t)
                .log("Gateway de lançamentos indisponível — operação não pode ser completada para esta data");
        throw new GatewayException("Gateway de lançamentos indisponível para " + data, t);
    }

    record ResumoDiarioDto(
            LocalDate data,
            @JsonProperty("total_creditos") BigDecimal totalCreditos,
            @JsonProperty("total_debitos") BigDecimal totalDebitos,
            @JsonProperty("total_lancamentos") long totalLancamentos) {}
}
