package com.uade.crm.vehiculos.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.uade.crm.vehiculos.PruebaIntegracion;
import com.uade.crm.vehiculos.application.AltaVehiculo;
import com.uade.crm.vehiculos.application.AltaVehiculoService;
import com.uade.crm.vehiculos.application.ConsultaVehiculoService;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.Vehiculo;

@PruebaIntegracion
class AccesoLocalYRemotoTest {

    private static final String CLAVE = "clave-ficticia-solo-para-tests-0123456789";

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    @Autowired
    private AltaVehiculoService alta;

    @Autowired
    private ConsultaVehiculoService consulta;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private final List<UUID> creados = new ArrayList<>();

    @AfterEach
    void borrarCreados() {
        for (UUID id : creados) {
            jdbc.update("DELETE FROM " + schema + ".precio_historial WHERE vehiculo_id = ?", id);
            jdbc.update("DELETE FROM " + schema + ".vehiculo WHERE id = ?", id);
        }
        creados.clear();
    }

    @Test
    void loQueSeRegistraPorInvocacionDirectaSeLeePorHttp() throws Exception {
        Vehiculo local = alta.registrar(new AltaVehiculo("Renault", "Sandero", "Stepway", 2020, 52000,
                new BigDecimal("8500000"), true, Caja.MANUAL));
        creados.add(local.id());

        ResponseEntity<String> respuesta = http.getForEntity("/vehiculos/" + local.id(), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode remoto = json.readTree(respuesta.getBody());
        assertThat(remoto.get("id").asText()).isEqualTo(local.id().toString());
        assertThat(remoto.get("marca").asText()).isEqualTo("Renault");
        assertThat(remoto.get("caja").asText()).isEqualTo("MANUAL");
        assertThat(remoto.get("precio").decimalValue()).isEqualByComparingTo("8500000");
    }

    @Test
    void loQueSeRegistraPorHttpSeLeePorInvocacionDirecta() throws Exception {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        cabeceras.set("X-Api-Key", CLAVE);
        String cuerpo = """
                {"marca": "Peugeot", "modelo": "208", "version": "Allure", "anio": 2022, "kilometraje": 18000,
                 "precio": 14200000, "caja": "AUTOMATICA"}
                """;

        ResponseEntity<String> respuesta = http.postForEntity("/vehiculos", new HttpEntity<>(cuerpo, cabeceras),
                String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString(json.readTree(respuesta.getBody()).get("id").asText());
        creados.add(id);
        Vehiculo local = consulta.obtener(id);
        assertThat(local.marca()).isEqualTo("Peugeot");
        assertThat(local.version()).contains("Allure");
        assertThat(local.caja()).isEqualTo(Caja.AUTOMATICA);
        assertThat(local.precio()).isEqualByComparingTo("14200000");
        assertThat(consulta.historialDePrecios(id)).hasSize(1);
    }
}
