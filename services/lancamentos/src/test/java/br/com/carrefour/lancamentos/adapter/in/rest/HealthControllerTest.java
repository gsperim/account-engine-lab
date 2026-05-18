package br.com.carrefour.lancamentos.adapter.in.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// standaloneSetup — sem contexto Spring nem dependências do Actuator
class HealthControllerTest {

    MockMvc mvc;
    HealthEndpoint healthEndpoint = mock(HealthEndpoint.class);

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new HealthController(healthEndpoint)).build();
    }

    @Test
    void liveness_deveRetornar200() throws Exception {
        mvc.perform(get("/health/live"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readiness_deveRetornar200QuandoDbERabbitEstaoUp() throws Exception {
        var composite = mock(CompositeHealth.class);
        when(composite.getComponents()).thenReturn(Map.of(
            "db",     (HealthComponent) Health.up().build(),
            "rabbit", (HealthComponent) Health.up().build()));
        when(healthEndpoint.health()).thenReturn(composite);

        mvc.perform(get("/health/ready"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.checks.database").value("UP"))
            .andExpect(jsonPath("$.checks.broker").value("UP"));
    }

    @Test
    void readiness_deveRetornar503QuandoHealthNaoEComposito() throws Exception {
        // health() retorna Health simples (não CompositeHealth) → checks vazio → allUp = false
        when(healthEndpoint.health()).thenReturn(Health.up().build());

        mvc.perform(get("/health/ready"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("DOWN"));
    }
}
