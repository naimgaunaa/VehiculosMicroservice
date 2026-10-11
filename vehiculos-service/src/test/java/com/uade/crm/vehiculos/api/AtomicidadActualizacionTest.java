package com.uade.crm.vehiculos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.uade.crm.vehiculos.PruebaIntegracion;
import com.uade.crm.vehiculos.RelojDePrueba;

@PruebaIntegracion
class AtomicidadActualizacionTest {

    private static final String CLAVE = "clave-ficticia-solo-para-tests-0123456789";
    private static final String ID = "c0000000-0000-4000-8000-000000000002";
    private static final Instant CREADO = RelojDePrueba.INICIO.minus(Duration.ofDays(30));
    private static final Instant VIGENCIA_FUTURA = RelojDePrueba.INICIO.plus(Duration.ofDays(1));

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RelojDePrueba reloj;

    @BeforeEach
    void insertarUnidadConUnaFilaDeHistorialQueImpideRegistrarOtra() {
        reloj.reiniciar();
        jdbc.update("INSERT INTO " + schema + ".vehiculo (id, marca, modelo, anio, kilometraje, precio, disponible, "
                + "caja, creado_en, actualizado_en) VALUES (?::uuid, 'Ford', 'Ka', 2018, 70000, 1000000.00, TRUE, "
                + "'MANUAL', ?, ?)", ID, Timestamp.from(CREADO), Timestamp.from(CREADO));
        jdbc.update("INSERT INTO " + schema + ".precio_historial (id, vehiculo_id, precio, vigente_desde) "
                + "VALUES ('c0000000-0000-4000-8000-0000000000f2'::uuid, ?::uuid, 1000000.00, ?)",
                ID, Timestamp.from(VIGENCIA_FUTURA));
    }

    @AfterEach
    void borrarLaUnidad() {
        jdbc.update("DELETE FROM " + schema + ".precio_historial WHERE vehiculo_id = ?::uuid", ID);
        jdbc.update("DELETE FROM " + schema + ".vehiculo WHERE id = ?::uuid", ID);
        reloj.reiniciar();
    }

    @Test
    void siRegistrarElPrecioFallaLaUnidadNoQuedaModificada() throws Exception {
        mvc.perform(put("/vehiculos/{id}", ID).header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precio\":2000000,\"disponible\":false}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"));

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT precio, disponible, actualizado_en FROM " + schema + ".vehiculo WHERE id = ?::uuid", ID);
        assertThat((BigDecimal) fila.get("precio")).isEqualByComparingTo("1000000");
        assertThat(fila.get("disponible")).isEqualTo(true);
        assertThat(((Timestamp) fila.get("actualizado_en")).toInstant()).isEqualTo(CREADO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".precio_historial WHERE vehiculo_id = ?::uuid",
                Integer.class, ID)).isEqualTo(1);
    }
}
