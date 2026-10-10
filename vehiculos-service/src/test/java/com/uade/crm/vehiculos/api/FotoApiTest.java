package com.uade.crm.vehiculos.api;

import static com.uade.crm.vehiculos.FotosDePrueba.png;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.uade.crm.vehiculos.FotoStorageEnMemoria;
import com.uade.crm.vehiculos.PruebaIntegracion;
import com.uade.crm.vehiculos.RelojDePrueba;

@PruebaIntegracion
class FotoApiTest {

    private static final String CLAVE = "clave-ficticia-solo-para-tests-0123456789";
    private static final String ID = "c0000000-0000-4000-8000-000000000003";
    private static final Instant CREADO = RelojDePrueba.INICIO.minus(Duration.ofDays(30));
    private static final int KB = 1024;

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private FotoStorageEnMemoria storage;

    @BeforeEach
    void insertarUnidad() {
        storage.reiniciar();
        jdbc.update("INSERT INTO " + schema + ".vehiculo (id, marca, modelo, anio, kilometraje, precio, disponible, "
                + "caja, creado_en, actualizado_en) VALUES (?::uuid, 'Ford', 'Ka', 2018, 70000, 1000000.00, TRUE, "
                + "'MANUAL', ?, ?)", ID, Timestamp.from(CREADO), Timestamp.from(CREADO));
    }

    @AfterEach
    void borrarUnidad() {
        jdbc.update("DELETE FROM " + schema + ".vehiculo WHERE id = ?::uuid", ID);
        storage.reiniciar();
    }

    @Test
    void unaFotoDeUnoComaCincoMbSuperaElLimiteDeSpringYSeGuarda() throws Exception {
        ResponseEntity<String> respuesta = subir(png(1536 * KB), CLAVE);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        String url = FotoStorageEnMemoria.BASE + ID + ".png";
        assertThat(json.readTree(respuesta.getBody()).get("fotoUrl").asText()).isEqualTo(url);
        assertThat(storage.objetos()).containsOnlyKeys(url);
        assertThat(jdbc.queryForObject("SELECT foto_url FROM " + schema + ".vehiculo WHERE id = ?::uuid",
                String.class, ID)).isEqualTo(url);
    }

    @Test
    void unaFotoDeMasDeDosMbPorDebajoDelLimiteDeSpringDa422() throws Exception {
        fotoInvalida(subir(png(2560 * KB), CLAVE));
    }

    @Test
    void unaFotoQueSuperaElLimiteDeSpringDa422YNo413() throws Exception {
        fotoInvalida(subir(png(4096 * KB), CLAVE));
    }

    @Test
    void sinClaveResponde401AntesDeLeerUnArchivoQueSuperaElLimiteDeSpring() throws Exception {
        ResponseEntity<String> respuesta = subir(png(4096 * KB), null);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json.readTree(respuesta.getBody()).get("codigo").asText()).isEqualTo("CLAVE_SERVICIO_INVALIDA");
        assertThat(storage.objetos()).isEmpty();
    }

    private void fotoInvalida(ResponseEntity<String> respuesta) throws Exception {
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(respuesta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode cuerpo = json.readTree(respuesta.getBody());
        assertThat(cuerpo.get("codigo").asText()).isEqualTo("FOTO_INVALIDA");
        assertThat(cuerpo.get("correlationId").asText()).isNotBlank();
        assertThat(storage.objetos()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT foto_url FROM " + schema + ".vehiculo WHERE id = ?::uuid",
                String.class, ID)).isNull();
    }

    private ResponseEntity<String> subir(byte[] contenido, String clave) {
        HttpHeaders parte = new HttpHeaders();
        parte.setContentType(MediaType.IMAGE_PNG);
        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("archivo", new HttpEntity<>(new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return "auto.png";
            }
        }, parte));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (clave != null) {
            headers.set("X-Api-Key", clave);
        }
        return http.exchange("/vehiculos/{id}/foto", HttpMethod.PUT, new HttpEntity<>(cuerpo, headers),
                String.class, ID);
    }
}
