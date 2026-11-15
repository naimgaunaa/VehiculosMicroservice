package com.uade.crm.vehiculos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.PruebaIntegracion;
import com.uade.crm.vehiculos.RelojDePrueba;

/** Patente y multas de punta a punta contra PostgreSQL real, como las usa el orquestador. */
@PruebaIntegracion
@Transactional
class MultasApiTest {

    private static final String CLAVE = "clave-ficticia-solo-para-tests-0123456789";

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

    @BeforeEach
    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    // ------------------------------------------------------------------ patente

    @Test
    void elAltaConPatenteLaNormalizaYElDetalleLaDevuelve() throws Exception {
        String id = crear("ab 123-cd");

        mvc.perform(get("/vehiculos/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.patente").value("AB123CD"));
    }

    @Test
    void elAltaSinPatenteLaDejaEnNull() throws Exception {
        String id = crear(null);

        mvc.perform(get("/vehiculos/{id}", id)).andExpect(jsonPath("$.patente").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void unaPatenteConFormatoInvalidoEnElAltaResponde422() throws Exception {
        mvc.perform(post("/vehiculos").header("X-Api-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content(alta("XYZ")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void dosUnidadesNoPuedenTenerLaMismaPatenteEnElAlta() throws Exception {
        crear("AB123CD");

        mvc.perform(post("/vehiculos").header("X-Api-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content(alta("ab123cd")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PATENTE_DUPLICADA"));
    }

    @Test
    void sePuedeCargarLaPatenteDespuesConUnPut() throws Exception {
        String id = crear(null);

        mvc.perform(actualizar(id, "{\"patente\":\"aaa 111\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.patente").value("AAA111"));

        mvc.perform(get("/vehiculos/{id}", id)).andExpect(jsonPath("$.patente").value("AAA111"));
    }

    @Test
    void unPutNoPuedeAsignarLaPatenteDeOtraUnidad() throws Exception {
        crear("AB123CD");
        String otra = crear(null);

        mvc.perform(actualizar(otra, "{\"patente\":\"AB123CD\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PATENTE_DUPLICADA"));
    }

    @Test
    void unPutPuedeReasignarLaMismaPatenteALaMismaUnidad() throws Exception {
        String id = crear("AB123CD");

        mvc.perform(actualizar(id, "{\"patente\":\"AB123CD\"}")).andExpect(status().isOk());
    }

    @Test
    void unPutConPatenteInvalidaResponde422() throws Exception {
        String id = crear(null);

        mvc.perform(actualizar(id, "{\"patente\":\"XYZ\"}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void elPutSigueExigiendoAlgoParaCambiar() throws Exception {
        String id = crear(null);

        mvc.perform(actualizar(id, "{}")).andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------ multas

    @Test
    void unaUnidadSinConsultaDevuelveFechaNulaYSinActas() throws Exception {
        String id = crear("AB123CD");

        mvc.perform(get("/vehiculos/{id}/multas", id).header("X-Api-Key", CLAVE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.patente").value("AB123CD"))
                .andExpect(jsonPath("$.consultadasEn").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.cantidad").value(0));
    }

    @Test
    void elResultadoQueEntregaElOrquestadorSeGuardaYSeConsulta() throws Exception {
        String id = crear("AB123CD");
        reloj.avanzar(java.time.Duration.ofHours(1));

        mvc.perform(registrar(id, cuerpo("AB123CD", acta("A-2", "10000"), acta("A-1", "33150"))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/vehiculos/{id}/multas", id).header("X-Api-Key", CLAVE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.consultadasEn").value("2026-10-09T13:00:00Z"))
                .andExpect(jsonPath("$.subtotal").value(43150.00))
                .andExpect(jsonPath("$.cantidad").value(2))
                // ordenadas por número de acta
                .andExpect(jsonPath("$.infracciones[0].numeroActa").value("A-1"))
                .andExpect(jsonPath("$.infracciones[0].importe").value(33150.00))
                .andExpect(jsonPath("$.infracciones[0].lugar").value("Córdoba"))
                .andExpect(jsonPath("$.infracciones[1].numeroActa").value("A-2"));
    }

    @Test
    void elDetalleDeLaUnidadNoExponeLasMultas() throws Exception {
        String id = crear("AB123CD");
        mvc.perform(registrar(id, cuerpo("AB123CD", acta("A-1", "33150")))).andExpect(status().isNoContent());

        String detalle = mvc.perform(get("/vehiculos/{id}", id)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(detalle).doesNotContain("A-1").doesNotContain("multas").doesNotContain("33150");
    }

    @Test
    void unResultadoNuevoReemplazaAlAnterior() throws Exception {
        String id = crear("AB123CD");
        mvc.perform(registrar(id, cuerpo("AB123CD", acta("A-1", "33150"), acta("A-2", "10000"))))
                .andExpect(status().isNoContent());

        mvc.perform(registrar(id, cuerpo("AB123CD", acta("A-3", "500")))).andExpect(status().isNoContent());

        mvc.perform(get("/vehiculos/{id}/multas", id).header("X-Api-Key", CLAVE))
                .andExpect(jsonPath("$.cantidad").value(1))
                .andExpect(jsonPath("$.infracciones[0].numeroActa").value("A-3"))
                .andExpect(jsonPath("$.subtotal").value(500.00));
    }

    @Test
    void elMismoResultadoDosVecesDejaElMismoEstado() throws Exception {
        String id = crear("AB123CD");
        String cuerpo = cuerpo("AB123CD", acta("A-1", "33150"));

        mvc.perform(registrar(id, cuerpo)).andExpect(status().isNoContent());
        mvc.perform(registrar(id, cuerpo)).andExpect(status().isNoContent());

        assertThat(contarMultas(id)).isEqualTo(1);
    }

    @Test
    void unaPatenteSinMultasBorraLasActasAnteriores() throws Exception {
        String id = crear("AB123CD");
        mvc.perform(registrar(id, cuerpo("AB123CD", acta("A-1", "33150")))).andExpect(status().isNoContent());

        mvc.perform(registrar(id, "{\"dominio\":\"AB123CD\",\"infracciones\":[],\"subtotal\":0}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/vehiculos/{id}/multas", id).header("X-Api-Key", CLAVE))
                .andExpect(jsonPath("$.cantidad").value(0))
                .andExpect(jsonPath("$.subtotal").value(0.00))
                .andExpect(jsonPath("$.consultadasEn").value("2026-10-09T12:00:00Z"));
    }

    @Test
    void cambiarLaPatenteDescartaLasMultasDeLaAnterior() throws Exception {
        String id = crear("AB123CD");
        mvc.perform(registrar(id, cuerpo("AB123CD", acta("A-1", "33150")))).andExpect(status().isNoContent());

        mvc.perform(actualizar(id, "{\"patente\":\"ZZ999ZZ\"}")).andExpect(status().isOk());

        assertThat(contarMultas(id)).isZero();
        mvc.perform(get("/vehiculos/{id}/multas", id).header("X-Api-Key", CLAVE))
                .andExpect(jsonPath("$.patente").value("ZZ999ZZ"))
                .andExpect(jsonPath("$.consultadasEn").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.subtotal").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void elPutDeOtroCampoNoTocaLasMultas() throws Exception {
        String id = crear("AB123CD");
        mvc.perform(registrar(id, cuerpo("AB123CD", acta("A-1", "33150")))).andExpect(status().isNoContent());
        reloj.avanzar(java.time.Duration.ofHours(1));

        mvc.perform(actualizar(id, "{\"precio\":11000000}")).andExpect(status().isOk());

        assertThat(contarMultas(id)).isEqualTo(1);
    }

    @Test
    void unaUnidadSinPatenteNoPuedeRecibirMultas() throws Exception {
        String id = crear(null);

        mvc.perform(registrar(id, cuerpo("AB123CD", acta("A-1", "1")))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PATENTE_NO_COINCIDE"));

        assertThat(contarMultas(id)).isZero();
    }

    @Test
    void multasDeOtraPatenteSeRechazanYNoSeGuardan() throws Exception {
        String id = crear("AB123CD");

        mvc.perform(registrar(id, cuerpo("ZZ999ZZ", acta("A-1", "1")))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PATENTE_NO_COINCIDE"));

        assertThat(contarMultas(id)).isZero();
    }

    @Test
    void unaUnidadInexistenteResponde404() throws Exception {
        String inexistente = java.util.UUID.randomUUID().toString();

        mvc.perform(registrar(inexistente, cuerpo("AB123CD", acta("A-1", "1")))).andExpect(status().isNotFound());
        mvc.perform(get("/vehiculos/{id}/multas", inexistente).header("X-Api-Key", CLAVE))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinLaClaveNoSeEscribeNiSeLee() throws Exception {
        String id = crear("AB123CD");

        mvc.perform(put("/vehiculos/{id}/multas", id).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("AB123CD", acta("A-1", "1"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/vehiculos/{id}/multas", id)).andExpect(status().isUnauthorized());

        assertThat(contarMultas(id)).isZero();
    }

    @Test
    void actasRepetidasSeRechazanYNoSeGuardaNada() throws Exception {
        String id = crear("AB123CD");

        mvc.perform(registrar(id, cuerpo("AB123CD", acta("A-1", "1"), acta("A-1", "2"))))
                .andExpect(status().isUnprocessableEntity());

        assertThat(contarMultas(id)).isZero();
    }

    // ------------------------------------------------------------------ ayudas

    private String crear(String patente) throws Exception {
        String respuesta = mvc.perform(post("/vehiculos").header("X-Api-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content(alta(patente)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asText();
    }

    private static String alta(String patente) {
        return "{\"marca\":\"Toyota\",\"modelo\":\"Corolla\",\"anio\":2021,\"kilometraje\":45000,\"precio\":10000000,"
                + "\"caja\":\"MANUAL\"" + (patente == null ? "" : ",\"patente\":\"" + patente + "\"") + "}";
    }

    private static MockHttpServletRequestBuilder actualizar(String id, String cuerpo) {
        return put("/vehiculos/{id}", id).header("X-Api-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private static MockHttpServletRequestBuilder registrar(String id, String cuerpo) {
        return put("/vehiculos/{id}/multas", id).header("X-Api-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private static String acta(String numero, String importe) {
        return "{\"numeroActa\":\"" + numero + "\",\"dominio\":\"AB123CD\",\"importe\":" + importe + ",\"importeSinDescuento\":null,"
                + "\"tipoInfraccion\":\"LEY 24449\",\"lugar\":\"Córdoba\",\"juzgado\":\"Juzgado 1\"}";
    }

    /** El subtotal es la suma de los importes. */
    private static String cuerpo(String dominio, String... actas) {
        double subtotal = 0;
        for (String acta : actas) {
            int desde = acta.indexOf("\"importe\":") + "\"importe\":".length();
            subtotal += Double.parseDouble(acta.substring(desde, acta.indexOf(',', desde)));
        }
        return "{\"dominio\":\"" + dominio + "\",\"infracciones\":[" + String.join(",", actas) + "],\"subtotal\":" + subtotal
                + "}";
    }

    private int contarMultas(String id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + schema + ".multa WHERE vehiculo_id = ?::uuid", Integer.class, id);
    }
}
