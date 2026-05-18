package br.com.carrefour.consolidado.adapter.in.rest;

import br.com.carrefour.consolidado.domain.port.in.ReconstruirConsolidadoUseCase;
import br.com.carrefour.consolidado.domain.port.in.ReconstruirConsolidadoUseCase.Resultado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("null") // falso positivo: jwt() retorna JwtRequestPostProcessor/@NonNull RequestPostProcessor
@WebMvcTest(AdminController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ReconstruirConsolidadoUseCase reconstruirUseCase;
    @MockitoBean JwtDecoder jwtDecoder;

    static final LocalDate INICIO = LocalDate.of(2026, 5, 1);
    static final LocalDate FIM    = LocalDate.of(2026, 5, 9);

    @Test
    void reconstruir_deveRetornar200ComResultado() throws Exception {
        when(reconstruirUseCase.executar(any())).thenReturn(
            new Resultado(9, 1, INICIO, FIM));

        mvc.perform(post("/admin/reconstruir")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .param("data_inicio", "2026-05-01")
                .param("data_fim",    "2026-05-09"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.datas_processadas").value(9))
            .andExpect(jsonPath("$.divergencias_corrigidas").value(1));
    }

    @Test
    void reconstruir_deveRetornar401SemToken() throws Exception {
        mvc.perform(post("/admin/reconstruir")
                .param("data_inicio", "2026-05-01")
                .param("data_fim",    "2026-05-09"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void reconstruir_deveRetornar403ParaRoleInsuficiente() throws Exception {
        mvc.perform(post("/admin/reconstruir")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTOR")))
                .param("data_inicio", "2026-05-01")
                .param("data_fim",    "2026-05-09"))
            .andExpect(status().isForbidden());
    }

    @Test
    void reconstruir_deveRetornar400QuandoPeriodoInvalido() throws Exception {
        when(reconstruirUseCase.executar(any()))
            .thenThrow(new IllegalArgumentException("data_fim anterior a data_inicio"));

        mvc.perform(post("/admin/reconstruir")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .param("data_inicio", "2026-05-09")
                .param("data_fim",    "2026-05-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("ARGUMENTO_INVALIDO"));
    }

    @Test
    void reconstruir_deveRetornar500EmErroInesperado() throws Exception {
        when(reconstruirUseCase.executar(any()))
            .thenThrow(new RuntimeException("falha inesperada"));

        mvc.perform(post("/admin/reconstruir")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .param("data_inicio", "2026-05-01")
                .param("data_fim",    "2026-05-09"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"));
    }
}
