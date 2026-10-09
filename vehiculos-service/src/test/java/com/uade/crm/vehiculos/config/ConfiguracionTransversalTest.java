package com.uade.crm.vehiculos.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.uade.crm.vehiculos.PropiedadesDePrueba;

/**
 * Contexto completo sin base de datos: Actuator, OpenAPI y filtro. Mientras no haya persistencia se
 * excluyen DataSource, JPA y Flyway; al llegar la etapa de persistencia los tests de integración
 * usarán Testcontainers y este puede apoyarse en ellos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@PropiedadesDePrueba
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class ConfiguracionTransversalTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void actuatorExponeHealthConElCorrelationId() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().exists("X-Correlation-Id"));
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
