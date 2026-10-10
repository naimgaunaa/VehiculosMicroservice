package com.uade.crm.vehiculos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.PruebaIntegracion;
import com.uade.crm.vehiculos.RelojDePrueba;

@PruebaIntegracion
@Transactional
class VehiculoEscrituraApiTest {

    private static final String CLAVE = "clave-ficticia-solo-para-tests-0123456789";
    private static final Duration UNA_HORA = Duration.ofHours(1);

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RelojDePrueba reloj;

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    @Test
    void elAltaCreaLaUnidadConUnaFilaDeHistorialYSuLocationLaDevuelve() throws Exception {
        MvcResult creado = mvc.perform(post("/vehiculos").header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta("10000000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disponible").value(true))
                .andReturn();
        String id = json.readTree(creado.getResponse().getContentAsString()).get("id").asText();
        String ubicacion = creado.getResponse().getHeader("Location");

        assertThat(ubicacion).endsWith("/vehiculos/" + id);
        mvc.perform(get(ubicacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.version").value("XEI CVT"));
        JsonNode historial = historial(id);
        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).get("precio").decimalValue()).isEqualByComparingTo("10000000");
        assertThat(historial.get(0).get("vigenteDesde").asText()).isEqualTo(RelojDePrueba.INICIO.toString());
        assertThat(contarHistorial(id)).isEqualTo(1);
    }

    @Test
    void unPutQueCambiaElPrecioSumaUnaFilaQueCoincideConElPrecioActual() throws Exception {
        String id = crear("10000000");
        reloj.avanzar(UNA_HORA);

        mvc.perform(actualizar(id, "{\"precio\":10500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(10500000.00));

        JsonNode historial = historial(id);
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).get("precio").decimalValue()).isEqualByComparingTo("10500000");
        assertThat(historial.get(0).get("vigenteDesde").asText())
                .isEqualTo(RelojDePrueba.INICIO.plus(UNA_HORA).toString());
        assertThat(historial.get(1).get("precio").decimalValue()).isEqualByComparingTo("10000000");
        JsonNode precio = json.readTree(mvc.perform(get("/vehiculos/{id}/precio", id))
                .andReturn().getResponse().getContentAsString());
        assertThat(precio.get("precio").decimalValue()).isEqualByComparingTo(historial.get(0).get("precio").decimalValue());
        assertThat(actualizadoEn(id)).isEqualTo(RelojDePrueba.INICIO.plus(UNA_HORA));
    }

    @Test
    void unPutQueSoloCambiaDisponibleNoSumaFila() throws Exception {
        String id = crear("10000000");
        reloj.avanzar(UNA_HORA);

        mvc.perform(actualizar(id, "{\"disponible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false));

        assertThat(contarHistorial(id)).isEqualTo(1);
        assertThat(actualizadoEn(id)).isEqualTo(RelojDePrueba.INICIO.plus(UNA_HORA));
        mvc.perform(get("/vehiculos/{id}", id)).andExpect(jsonPath("$.disponible").value(false));
    }

    @Test
    void unPutConElMismoPrecioEnOtraEscalaNoSumaFilaNiRenuevaActualizadoEn() throws Exception {
        String id = crear("10500000.00");
        reloj.avanzar(UNA_HORA);

        mvc.perform(actualizar(id, "{\"precio\":10500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(10500000.00));

        assertThat(contarHistorial(id)).isEqualTo(1);
        assertThat(actualizadoEn(id)).isEqualTo(RelojDePrueba.INICIO);
    }

    @Test
    void unPutConLaMismaDisponibilidadNoRenuevaActualizadoEn() throws Exception {
        String id = crear("10500000");
        reloj.avanzar(UNA_HORA);

        mvc.perform(actualizar(id, "{\"precio\":10500000.00,\"disponible\":true}")).andExpect(status().isOk());

        assertThat(contarHistorial(id)).isEqualTo(1);
        assertThat(actualizadoEn(id)).isEqualTo(RelojDePrueba.INICIO);
    }

    @Test
    void dosCambiosDePrecioDejanTresFilasDelMasNuevoAlMasViejo() throws Exception {
        String id = crear("10000000");
        reloj.avanzar(UNA_HORA);
        mvc.perform(actualizar(id, "{\"precio\":10500000}")).andExpect(status().isOk());
        reloj.avanzar(UNA_HORA);
        mvc.perform(actualizar(id, "{\"precio\":9900000.50,\"disponible\":false}")).andExpect(status().isOk());

        JsonNode historial = historial(id);
        assertThat(historial).extracting(fila -> fila.get("precio").decimalValue().stripTrailingZeros().toPlainString())
                .containsExactly("9900000.5", "10500000", "10000000");
    }

    @Test
    void unPutSobreUnaUnidadInexistenteResponde404() throws Exception {
        mvc.perform(actualizar("a0000000-0000-4000-8000-000000000099", "{\"precio\":10500000}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VEHICULO_NO_ENCONTRADO"));
    }

    @Test
    void elAltaSinClaveNoGuardaNada() throws Exception {
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM " + schema + ".vehiculo", Integer.class);

        mvc.perform(post("/vehiculos").contentType(MediaType.APPLICATION_JSON).content(alta("10000000")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".vehiculo", Integer.class)).isEqualTo(antes);
    }

    @Test
    void laDocumentacionPideLaClaveSoloEnLasEscrituras() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/vehiculos'].post.security[0].claveServicio").exists())
                .andExpect(jsonPath("$.paths['/vehiculos/{id}'].put.security[0].claveServicio").exists())
                .andExpect(jsonPath("$.paths['/vehiculos'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/vehiculos/{id}'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/vehiculos'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/vehiculos'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/vehiculos/{id}'].put.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.AltaVehiculoRequest.properties.marca").exists())
                .andExpect(jsonPath("$.components.schemas.ActualizacionVehiculoRequest.properties.precio").exists())
                .andExpect(jsonPath("$.components.schemas.ActualizacionVehiculoRequest.properties.precioODisponible")
                        .doesNotExist());
    }

    private String crear(String precio) throws Exception {
        String cuerpo = mvc.perform(post("/vehiculos").header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta(precio)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("id").asText();
    }

    private static RequestBuilder actualizar(String id, String cuerpo) {
        return put("/vehiculos/{id}", id).header("X-Api-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private JsonNode historial(String id) throws Exception {
        return json.readTree(mvc.perform(get("/vehiculos/{id}/historial-precios", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private int contarHistorial(String id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + schema + ".precio_historial WHERE vehiculo_id = ?::uuid",
                Integer.class, id);
    }

    private Instant actualizadoEn(String id) {
        return jdbc.queryForObject("SELECT actualizado_en FROM " + schema + ".vehiculo WHERE id = ?::uuid",
                Timestamp.class, id).toInstant();
    }

    private static String alta(String precio) {
        return """
                {"marca":"Toyota","modelo":"Corolla","version":"XEI CVT","anio":2021,"kilometraje":45000,
                 "precio":%s,"caja":"AUTOMATICA"}
                """.formatted(new BigDecimal(precio).toPlainString());
    }
}
