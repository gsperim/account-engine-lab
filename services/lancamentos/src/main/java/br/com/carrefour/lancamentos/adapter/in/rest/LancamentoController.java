package br.com.carrefour.lancamentos.adapter.in.rest;

import br.com.carrefour.lancamentos.adapter.in.rest.dto.generated.LancamentoRequest;
import br.com.carrefour.lancamentos.adapter.in.rest.dto.generated.LancamentoResponse;
import br.com.carrefour.lancamentos.adapter.in.rest.dto.generated.PageLancamentoResponse;
import br.com.carrefour.lancamentos.adapter.in.rest.dto.generated.ResumoDiarioResponse;
import br.com.carrefour.lancamentos.adapter.in.rest.dto.generated.TipoLancamento;
import br.com.carrefour.lancamentos.adapter.in.rest.generated.LancamentosApi;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.Valor;
import br.com.carrefour.lancamentos.domain.port.in.BuscarLancamentoUseCase;
import br.com.carrefour.lancamentos.domain.port.in.EstornarLancamentoUseCase;
import br.com.carrefour.lancamentos.domain.port.in.ListarLancamentosUseCase;
import br.com.carrefour.lancamentos.domain.port.in.RegistrarLancamentoUseCase;
import br.com.carrefour.lancamentos.domain.port.in.ResumoDiarioUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
public class LancamentoController implements LancamentosApi {

    private final RegistrarLancamentoUseCase registrarUseCase;
    private final BuscarLancamentoUseCase buscarUseCase;
    private final ListarLancamentosUseCase listarUseCase;
    private final EstornarLancamentoUseCase estornarUseCase;
    private final ResumoDiarioUseCase resumoDiarioUseCase;
    private final LancamentoMapper mapper;

    // Métricas de negócio — publicadas no adapter para não poluir o domínio
    private final Counter creditosTotal;
    private final Counter debitosTotal;
    private final Counter creditosValor;
    private final Counter debitosValor;
    private final Counter estornosTotal;

    public LancamentoController(
            RegistrarLancamentoUseCase registrarUseCase,
            BuscarLancamentoUseCase buscarUseCase,
            ListarLancamentosUseCase listarUseCase,
            EstornarLancamentoUseCase estornarUseCase,
            ResumoDiarioUseCase resumoDiarioUseCase,
            LancamentoMapper mapper,
            MeterRegistry meterRegistry) {
        this.registrarUseCase    = registrarUseCase;
        this.buscarUseCase       = buscarUseCase;
        this.listarUseCase       = listarUseCase;
        this.estornarUseCase     = estornarUseCase;
        this.resumoDiarioUseCase = resumoDiarioUseCase;
        this.mapper              = mapper;

        this.creditosTotal = Counter.builder("lancamentos_registrados_total")
                .tag("tipo", "CREDITO").description("Total de lançamentos de crédito registrados")
                .register(meterRegistry);
        this.debitosTotal = Counter.builder("lancamentos_registrados_total")
                .tag("tipo", "DEBITO").description("Total de lançamentos de débito registrados")
                .register(meterRegistry);
        this.creditosValor = Counter.builder("lancamentos_valor_reais_total")
                .tag("tipo", "CREDITO").description("Valor acumulado de créditos em BRL")
                .register(meterRegistry);
        this.debitosValor = Counter.builder("lancamentos_valor_reais_total")
                .tag("tipo", "DEBITO").description("Valor acumulado de débitos em BRL")
                .register(meterRegistry);
        this.estornosTotal = Counter.builder("estornos_registrados_total")
                .description("Total de estornos registrados")
                .register(meterRegistry);
    }

    @Override
    public ResponseEntity<LancamentoResponse> registrarLancamento(UUID idempotencyKey, LancamentoRequest req) {
        var operadorId = extrairOperadorId();
        var command = new RegistrarLancamentoUseCase.Command(
                mapper.toDomain(req.getTipo()),
                Valor.de(BigDecimal.valueOf(req.getValor())),
                req.getDescricao(),
                req.getDataCompetencia(),
                operadorId,
                idempotencyKey.toString()
        );
        var lancamento = registrarUseCase.executar(command);
        var valor = lancamento.getValor().toBigDecimal().doubleValue();
        if (lancamento.getTipo().name().equals("CREDITO")) {
            creditosTotal.increment();
            creditosValor.increment(valor);
        } else {
            debitosTotal.increment();
            debitosValor.increment(valor);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(lancamento));
    }

    @Override
    public ResponseEntity<LancamentoResponse> estornarLancamento(UUID id) {
        var command = new EstornarLancamentoUseCase.Command(LancamentoId.de(id), extrairOperadorId());
        var estorno = estornarUseCase.executar(command);
        estornosTotal.increment();
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(estorno));
    }

    @Override
    public ResponseEntity<ResumoDiarioResponse> resumoDiario(java.time.LocalDate data) {
        var resultado = resumoDiarioUseCase.executar(new ResumoDiarioUseCase.Query(data));
        return ResponseEntity.ok(mapper.toResumoDiarioResponse(resultado));
    }

    @Override
    public ResponseEntity<LancamentoResponse> buscarLancamento(UUID id) {
        return buscarUseCase.executar(LancamentoId.de(id))
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<PageLancamentoResponse> listarLancamentos(
            LocalDate data, TipoLancamento tipo, Integer page, Integer size) {
        var query = new ListarLancamentosUseCase.Query(data, mapper.toDomain(tipo), page, size);
        return ResponseEntity.ok(mapper.toPageResponse(listarUseCase.executar(query)));
    }

    private String extrairOperadorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            var sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) return sub;
            var username = jwt.getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) return username;
        }
        return "anonymous";
    }
}
