package br.com.carrefour.lancamentos.adapter.in.rest;

import br.com.carrefour.lancamentos.domain.exception.LancamentoConflitanteException;
import br.com.carrefour.lancamentos.domain.exception.LancamentoDuplicadoException;
import br.com.carrefour.lancamentos.domain.exception.LancamentoJaEstornadoException;
import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.model.Valor;
import br.com.carrefour.lancamentos.domain.port.in.BuscarLancamentoUseCase;
import br.com.carrefour.lancamentos.domain.port.in.EstornarLancamentoUseCase;
import br.com.carrefour.lancamentos.domain.port.in.ListarLancamentosUseCase;
import br.com.carrefour.lancamentos.domain.port.in.RegistrarLancamentoUseCase;
import br.com.carrefour.lancamentos.domain.port.in.ResumoDiarioUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LancamentoController.class)
@Import({LancamentoMapper.class, GlobalExceptionHandler.class, SecurityConfig.class,
         LancamentoControllerTest.MetricsConfig.class})
class LancamentoControllerTest {

    @TestConfiguration
    static class MetricsConfig {
        @Bean
        MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }

    @Autowired MockMvc mvc;

    @MockitoBean RegistrarLancamentoUseCase registrarUseCase;
    @MockitoBean BuscarLancamentoUseCase buscarUseCase;
    @MockitoBean ListarLancamentosUseCase listarUseCase;
    @MockitoBean EstornarLancamentoUseCase estornarUseCase;
    @MockitoBean ResumoDiarioUseCase resumoDiarioUseCase;
    // Necessário para que o SecurityConfig carregue sem jwk-set-uri real em testes
    @MockitoBean JwtDecoder jwtDecoder;

    static final UUID ID  = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID KEY = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void registrar_deveRetornar201ComLancamentoCriado() throws Exception {
        when(registrarUseCase.executar(any())).thenReturn(umLancamento());

        mvc.perform(post("/registros")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAIXA")))
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tipo":"CREDITO","valor":150.00,"data_competencia":"2026-05-09"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(ID.toString()))
            .andExpect(jsonPath("$.tipo").value("CREDITO"))
            .andExpect(jsonPath("$.valor").value(150.00));
    }

    @Test
    void registrar_deveRetornar409QuandoChaveDuplicada() throws Exception {
        when(registrarUseCase.executar(any()))
                .thenThrow(new LancamentoDuplicadoException(KEY.toString()));

        mvc.perform(post("/registros")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAIXA")))
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tipo":"CREDITO","valor":150.00,"data_competencia":"2026-05-09"}
                        """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("LANCAMENTO_DUPLICADO"));
    }

    @Test
    void registrar_deveRetornar400QuandoTipoAusente() throws Exception {
        mvc.perform(post("/registros")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAIXA")))
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"valor":150.00,"data_competencia":"2026-05-09"}
                        """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void registrar_deveRetornar401SemToken() throws Exception {
        mvc.perform(post("/registros")
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tipo":"CREDITO","valor":150.00,"data_competencia":"2026-05-09"}
                        """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void registrar_deveRetornar403ComRoleInsuficiente() throws Exception {
        mvc.perform(post("/registros")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTOR")))
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tipo":"CREDITO","valor":150.00,"data_competencia":"2026-05-09"}
                        """))
            .andExpect(status().isForbidden());
    }

    @Test
    void buscar_deveRetornar200QuandoEncontrado() throws Exception {
        when(buscarUseCase.executar(LancamentoId.de(ID))).thenReturn(Optional.of(umLancamento()));

        mvc.perform(get("/registros/{id}", ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(ID.toString()))
            .andExpect(jsonPath("$.tipo").value("CREDITO"));
    }

    @Test
    void buscar_deveRetornar404QuandoNaoEncontrado() throws Exception {
        when(buscarUseCase.executar(any())).thenReturn(Optional.empty());

        mvc.perform(get("/registros/{id}", ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAIXA"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void listar_deveRetornarPaginaComLancamentos() throws Exception {
        var result = new ListarLancamentosUseCase.Result(List.of(umLancamento()), 1L, 0, 20);
        when(listarUseCase.executar(any())).thenReturn(result);

        mvc.perform(get("/registros")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTOR")))
                .param("data", "2026-05-09"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(ID.toString()))
            .andExpect(jsonPath("$.total_elements").value(1))
            .andExpect(jsonPath("$.total_pages").value(1));
    }

    // --- estornar ---

    @Test
    void estornar_deveRetornar201ComEstornoRegistrado() throws Exception {
        when(estornarUseCase.executar(any())).thenReturn(umLancamento());

        mvc.perform(post("/registros/{id}/estorno", ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAIXA"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void estornar_deveRetornar409QuandoJaEstornado() throws Exception {
        when(estornarUseCase.executar(any())).thenThrow(new LancamentoJaEstornadoException(ID));

        mvc.perform(post("/registros/{id}/estorno", ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAIXA"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("LANCAMENTO_JA_ESTORNADO"));
    }

    @Test
    void estornar_deveRetornar404QuandoNaoEncontrado() throws Exception {
        when(estornarUseCase.executar(any())).thenThrow(new NoSuchElementException("não encontrado"));

        mvc.perform(post("/registros/{id}/estorno", ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAIXA"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    // --- resumo diário ---

    @Test
    void resumoDiario_deveRetornar200ComTotais() throws Exception {
        var resultado = new ResumoDiarioUseCase.Resultado(
                LocalDate.of(2026, 5, 9),
                new BigDecimal("500.00"), new BigDecimal("200.00"), 7);
        when(resumoDiarioUseCase.executar(any())).thenReturn(resultado);

        mvc.perform(get("/registros/resumo")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTOR")))
                .param("data", "2026-05-09"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_creditos").value(500.00))
            .andExpect(jsonPath("$.total_debitos").value(200.00))
            .andExpect(jsonPath("$.total_lancamentos").value(7));
    }

    // --- handlers de exceção não cobertos ---

    @Test
    void registrar_deveRetornar409QuandoConflitanteIdempotency() throws Exception {
        when(registrarUseCase.executar(any()))
                .thenThrow(new LancamentoConflitanteException(KEY.toString()));

        mvc.perform(post("/registros")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAIXA")))
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tipo":"CREDITO","valor":150.00,"data_competencia":"2026-05-09"}
                        """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("IDEMPOTENCY_KEY_CONFLITO"));
    }

    @Test
    void buscar_deveRetornar400PorTypeMismatch() throws Exception {
        mvc.perform(get("/registros/{id}", "nao-e-um-uuid")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTOR"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("TIPO_INVALIDO"));
    }

    @Test
    void registrar_deveRetornar500EmErroInesperado() throws Exception {
        when(registrarUseCase.executar(any())).thenThrow(new RuntimeException("erro inesperado"));

        mvc.perform(post("/registros")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAIXA")))
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tipo":"CREDITO","valor":150.00,"data_competencia":"2026-05-09"}
                        """))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"));
    }

    private Lancamento umLancamento() {
        return Lancamento.reconstituir(
                LancamentoId.de(ID),
                TipoLancamento.CREDITO,
                Valor.de("150.00"),
                "Venda balcão",
                LocalDate.of(2026, 5, 9),
                "usr_abc",
                LocalDateTime.of(2026, 5, 9, 12, 0),
                "test-hash", false, null);
    }
}
