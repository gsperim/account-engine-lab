package br.com.carrefour.consolidado.adapter.in.rest;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.port.in.BuscarConsolidadoPeriodoUseCase;
import br.com.carrefour.consolidado.domain.port.in.BuscarConsolidadoUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConsolidacaoController.class)
@Import({ConsolidacaoMapper.class, GlobalExceptionHandler.class, SecurityConfig.class})
class ConsolidacaoControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean BuscarConsolidadoUseCase buscarUseCase;
    @MockitoBean BuscarConsolidadoPeriodoUseCase buscarPeriodoUseCase;
    @MockitoBean JwtDecoder jwtDecoder;

    static final LocalDate HOJE = LocalDate.of(2026, 5, 9);

    @Test
    void consultarDiario_deveRetornar200ComSaldo() throws Exception {
        when(buscarUseCase.executar(HOJE)).thenReturn(Optional.of(umSaldo()));

        mvc.perform(get("/saldo/{data}", HOJE)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("2026-05-09"))
            .andExpect(jsonPath("$.total_creditos").value(300.0))
            .andExpect(jsonPath("$.total_debitos").value(100.0))
            .andExpect(jsonPath("$.saldo").value(200.0))
            .andExpect(jsonPath("$.total_lancamentos").value(3));
    }

    @Test
    void consultarDiario_deveRetornar404QuandoNaoEncontrado() throws Exception {
        when(buscarUseCase.executar(any())).thenReturn(Optional.empty());

        mvc.perform(get("/saldo/{data}", HOJE)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void consultarDiario_deveRetornar401SemToken() throws Exception {
        mvc.perform(get("/saldo/{data}", HOJE))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void consultarDiario_deveRetornar403ComRoleInsuficiente() throws Exception {
        mvc.perform(get("/saldo/{data}", HOJE)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAIXA"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void listarPeriodo_deveRetornarLista() throws Exception {
        when(buscarPeriodoUseCase.executar(any(), any())).thenReturn(List.of(umSaldo()));

        mvc.perform(get("/saldo")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTOR")))
                .param("data_inicio", "2026-05-01")
                .param("data_fim", "2026-05-09"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].data").value("2026-05-09"))
            .andExpect(jsonPath("$[0].saldo").value(200.0));
    }

    @Test
    void listarPeriodo_deveRetornar400QuandoPeriodoInvalido() throws Exception {
        when(buscarPeriodoUseCase.executar(any(), any()))
            .thenThrow(new IllegalArgumentException("data_fim não pode ser anterior a data_inicio"));

        mvc.perform(get("/saldo")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTOR")))
                .param("data_inicio", "2026-05-09")
                .param("data_fim", "2026-05-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("ARGUMENTO_INVALIDO"));
    }

    private SaldoConsolidado umSaldo() {
        return SaldoConsolidado.reconstituir(
            HOJE, new BigDecimal("300.00"), new BigDecimal("100.00"), 3, LocalDateTime.of(2026, 5, 9, 12, 0));
    }
}
