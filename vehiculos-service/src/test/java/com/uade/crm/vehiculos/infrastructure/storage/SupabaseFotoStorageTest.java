package com.uade.crm.vehiculos.infrastructure.storage;

import static com.uade.crm.vehiculos.FotosDePrueba.jpeg;
import static com.uade.crm.vehiculos.FotosDePrueba.png;
import static com.uade.crm.vehiculos.FotosDePrueba.webp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import com.uade.crm.vehiculos.config.VehiculosProperties;
import com.uade.crm.vehiculos.config.VehiculosProperties.ModoFotos;
import com.uade.crm.vehiculos.domain.model.AlmacenamientoNoDisponibleException;
import com.uade.crm.vehiculos.domain.model.ArchivoFoto;

class SupabaseFotoStorageTest {

    static final String URL = "https://proyecto-ficticio.supabase.co";
    static final String BUCKET = "vehiculos-fotos";
    static final String CLAVE_SECRETA = "sb_secret_ficticia_solo_para_tests_0123456789";
    static final String CLAVE_ESCRITURA = "clave-ficticia-solo-para-tests-0123456789";
    private static final UUID ID = UUID.fromString("9e8d7c6b-5a4f-4e3d-8c1b-0a9f8e7d6c5b");
    private static final String SUBIDA_PNG = URL + "/storage/v1/object/" + BUCKET + "/" + ID + ".png";
    private static final String PUBLICA_PNG = URL + "/storage/v1/object/public/" + BUCKET + "/" + ID + ".png";
    private static final String BORRADO = URL + "/storage/v1/object/" + BUCKET;

    private MockRestServiceServer servidor;
    private SupabaseFotoStorage storage;

    @BeforeEach
    void crearStorage() {
        storage = crear(URL);
    }

    @AfterEach
    void verificar() {
        servidor.verify();
    }

    @Test
    void laSubidaUsaElContratoDeStorage() {
        byte[] contenido = png(1024);
        servidor.expect(requestTo(SUBIDA_PNG))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", CLAVE_SECRETA))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(header("x-upsert", "true"))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(contenido))
                .andRespond(withSuccess("{\"Key\":\"" + BUCKET + "/" + ID + ".png\",\"Id\":\"x\"}",
                        MediaType.APPLICATION_JSON));

        String url = storage.guardar(ID, ArchivoFoto.de(contenido));

        assertThat(url).isEqualTo(PUBLICA_PNG);
    }

    @Test
    void elNombreYElContentTypeSalenDelTipoDetectado() {
        servidor.expect(requestTo(URL + "/storage/v1/object/" + BUCKET + "/" + ID + ".jpg"))
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andRespond(withSuccess());
        servidor.expect(requestTo(URL + "/storage/v1/object/" + BUCKET + "/" + ID + ".webp"))
                .andExpect(content().contentType("image/webp"))
                .andRespond(withSuccess());

        assertThat(storage.guardar(ID, ArchivoFoto.de(jpeg(100)))).endsWith("/" + ID + ".jpg");
        assertThat(storage.guardar(ID, ArchivoFoto.de(webp(100)))).endsWith("/" + ID + ".webp");
    }

    @Test
    void unaUrlConBarraFinalNoDuplicaLaBarra() {
        storage = crear(URL + "/");
        servidor.expect(requestTo(SUBIDA_PNG)).andRespond(withSuccess());

        assertThat(storage.guardar(ID, ArchivoFoto.de(png(100)))).isEqualTo(PUBLICA_PNG);
    }

    @Test
    void elBorradoMandaElPrefijoDelObjeto() {
        servidor.expect(requestTo(BORRADO))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("apikey", CLAVE_SECRETA))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"prefixes\":[\"" + ID + ".png\"]}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        storage.eliminar(PUBLICA_PNG);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://otro-proyecto.supabase.co/storage/v1/object/public/vehiculos-fotos/x.png",
            URL + "/storage/v1/object/public/otro-bucket/x.png",
            URL + "/storage/v1/object/public/vehiculos-fotos/",
            URL + "/storage/v1/object/public/vehiculos-fotos/carpeta/x.png",
            "no es una url"})
    void unaUrlAjenaNoSeBorra(String url) {
        storage.eliminar(url);
    }

    static Stream<Arguments> fallas() {
        return Stream.of(
                Arguments.of("4xx", (Consumer<ResponseActions>) r -> r.andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\":\"403\",\"error\":\"Unauthorized\",\"message\":\"invalid signature\"}"))),
                Arguments.of("5xx", (Consumer<ResponseActions>) r -> r.andRespond(withStatus(HttpStatus.BAD_GATEWAY))),
                Arguments.of("timeout", (Consumer<ResponseActions>) r -> r.andRespond(
                        withException(new SocketTimeoutException("Read timed out")))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fallas")
    void unaFallaDeLaSubidaEsAlmacenamientoNoDisponible(String caso, Consumer<ResponseActions> respuesta) {
        respuesta.accept(servidor.expect(requestTo(SUBIDA_PNG)));

        assertThatThrownBy(() -> storage.guardar(ID, ArchivoFoto.de(png(100))))
                .isInstanceOf(AlmacenamientoNoDisponibleException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fallas")
    void unaFallaDelBorradoEsAlmacenamientoNoDisponible(String caso, Consumer<ResponseActions> respuesta) {
        respuesta.accept(servidor.expect(requestTo(BORRADO)));

        assertThatThrownBy(() -> storage.eliminar(PUBLICA_PNG))
                .isInstanceOf(AlmacenamientoNoDisponibleException.class);
    }

    private SupabaseFotoStorage crear(String url) {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        return new SupabaseFotoStorage(builder, propiedades(url));
    }

    static VehiculosProperties propiedades(String url) {
        return new VehiculosProperties(ModoFotos.SUPABASE, BUCKET, url, CLAVE_SECRETA, CLAVE_ESCRITURA);
    }
}
