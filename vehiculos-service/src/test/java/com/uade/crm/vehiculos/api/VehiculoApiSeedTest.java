package com.uade.crm.vehiculos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.PruebaIntegracionSeed;

/** La API de lectura de punta a punta sobre el seed. Los números (10, 8, 2) dependen de {@code R__seed_vehiculos.sql}. */
@PruebaIntegracionSeed
@Transactional
class VehiculoApiSeedTest {

    private static final String SANDERO = "a0000000-0000-4000-8000-000000000005";

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void listaLasDiezUnidadesDeLaMasNuevaALaMasVieja() throws Exception {
        String cuerpo = mvc.perform(get("/vehiculos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].marca").value("Citroën"))
                .andExpect(jsonPath("$[0].modelo").value("C3"))
                .andExpect(jsonPath("$[9].marca").value("Toyota"))
                .andExpect(jsonPath("$[9].modelo").value("Corolla"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("creadoEn").doesNotContain("actualizadoEn");
        for (JsonNode unidad : json.readTree(cuerpo)) {
            assertThat(unidad.get("fotoUrl").isNull()).isTrue();
            assertThat(unidad.get("caja").asText()).isIn("AUTOMATICA", "MANUAL");
            assertThat(unidad.get("precio").isNumber()).isTrue();
        }
    }

    @Test
    void ochoUnidadesDisponibles() throws Exception {
        mvc.perform(get("/vehiculos").param("disponible", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[?(@.disponible == false)]").isEmpty());
    }

    @Test
    void dosUnidadesNoDisponiblesSanderoYCivic() throws Exception {
        String cuerpo = mvc.perform(get("/vehiculos").param("disponible", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(cuerpo)).extracting(unidad -> unidad.get("modelo").asText())
                .containsExactlyInAnyOrder("Sandero", "Civic");
    }

    @Test
    void unaUnidadNoDisponibleResponde200ConSuDetalleYSuPrecio() throws Exception {
        String cuerpo = mvc.perform(get("/vehiculos/{id}", SANDERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SANDERO))
                .andExpect(jsonPath("$.modelo").value("Sandero"))
                .andExpect(jsonPath("$.disponible").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).contains("\"precio\":11500000.00").doesNotContain("creadoEn").doesNotContain("actualizadoEn");

        String precio = mvc.perform(get("/vehiculos/{id}/precio", SANDERO))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(precio).isEqualTo("{\"precio\":11500000.00,\"disponible\":false}");
    }

    @Test
    void cadaUnidadTieneUnaFilaDeHistorialConSuPrecio() throws Exception {
        JsonNode unidades = json.readTree(mvc.perform(get("/vehiculos")).andReturn().getResponse().getContentAsString());
        List<String> revisadas = new ArrayList<>();

        for (JsonNode unidad : unidades) {
            String id = unidad.get("id").asText();
            JsonNode historial = json.readTree(mvc.perform(get("/vehiculos/{id}/historial-precios", id))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            assertThat(historial).as("historial de %s", unidad.get("modelo").asText()).hasSize(1);
            assertThat(historial.get(0).get("precio").decimalValue())
                    .isEqualByComparingTo(unidad.get("precio").decimalValue());
            assertThat(historial.get(0).get("vigenteDesde").asText()).endsWith("Z");
            revisadas.add(id);
        }

        assertThat(revisadas).hasSize(10);
    }

    @Test
    void unidadInexistenteResponde404() throws Exception {
        UUID inexistente = UUID.fromString("a0000000-0000-4000-8000-000000000099");

        mvc.perform(get("/vehiculos/{id}/precio", inexistente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VEHICULO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    /**
     * Un dato guardado fuera de las invariantes (la V1 no restringe {@code anio}) hace fallar la lectura en el
     * adaptador real. Es un error del servidor: 500 sin detalle, nunca 422. El insert se revierte al terminar.
     */
    @Test
    void datoGuardadoFueraDeLasInvariantesResponde500SinDetalle() throws Exception {
        String id = "c0000000-0000-4000-8000-000000000001";
        jdbc.update("INSERT INTO " + schema + ".vehiculo (id, marca, modelo, anio, kilometraje, precio, disponible, "
                + "caja, creado_en, actualizado_en) "
                + "VALUES (?::uuid, 'Ford', 'Falcon', 1900, 0, 1000.00, TRUE, 'MANUAL', now(), now())", id);

        String cuerpo = mvc.perform(get("/vehiculos/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado"))
                .andExpect(jsonPath("$.errores").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("1950").doesNotContain("1900").doesNotContain("VehiculoInvalidoException");
    }

    @Test
    void laDocumentacionOpenApiIncluyeLasRespuestasDeError() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/vehiculos'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/vehiculos'].get.parameters[0].name").value("disponible"))
                .andExpect(jsonPath("$.paths['/vehiculos/{id}'].get.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/VehiculoResponse"))
                .andExpect(jsonPath("$.paths['/vehiculos/{id}'].get.responses['404'].content['application/problem+json']")
                        .exists())
                .andExpect(jsonPath("$.paths['/vehiculos/{id}/precio'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/vehiculos/{id}/historial-precios'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/vehiculos/{id}'].get.responses['400']").exists())
                .andExpect(jsonPath("$.components.schemas.VehiculoResponse.properties.creadoEn").doesNotExist());
    }
}
