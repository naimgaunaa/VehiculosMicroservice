package com.uade.crm.vehiculos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.web.servlet.MockMvc;

import com.uade.crm.vehiculos.PruebaIntegracion;

/**
 * Contexto completo contra PostgreSQL de Testcontainers: Actuator (incluida la base en el health), OpenAPI
 * y filtro.
 */
@PruebaIntegracion
@AutoConfigureMockMvc
class ConfiguracionTransversalTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Test
    void actuatorExponeHealthConElCorrelationId() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    /** La respuesta HTTP no detalla componentes (no se expone), así que se consulta el endpoint por dentro. */
    @Test
    void healthIncluyeLaBaseDeDatos() {
        CompositeHealth salud = (CompositeHealth) healthEndpoint.health();

        assertThat(salud.getComponents()).containsKey("db");
        assertThat(salud.getComponents().get("db").getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void actuatorNoExponeOtrosEndpoints() throws Exception {
        for (String endpoint : new String[] {"env", "beans", "configprops", "info"}) {
            mvc.perform(get("/actuator/" + endpoint)).andExpect(status().isNotFound());
        }
    }

    @Test
    void openApiDocumentaLaClaveDeServicio() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Servicio de Vehículos"))
                .andExpect(jsonPath("$.components.securitySchemes.claveServicio.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.claveServicio.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.claveServicio.name").value("X-Api-Key"));
    }
}
