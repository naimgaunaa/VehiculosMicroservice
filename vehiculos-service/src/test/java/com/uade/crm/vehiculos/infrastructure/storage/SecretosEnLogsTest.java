package com.uade.crm.vehiculos.infrastructure.storage;

import static com.uade.crm.vehiculos.FotosDePrueba.jpeg;
import static com.uade.crm.vehiculos.FotosDePrueba.png;
import static com.uade.crm.vehiculos.infrastructure.storage.SupabaseFotoStorageTest.BUCKET;
import static com.uade.crm.vehiculos.infrastructure.storage.SupabaseFotoStorageTest.CLAVE_ESCRITURA;
import static com.uade.crm.vehiculos.infrastructure.storage.SupabaseFotoStorageTest.CLAVE_SECRETA;
import static com.uade.crm.vehiculos.infrastructure.storage.SupabaseFotoStorageTest.URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClient;

import com.uade.crm.vehiculos.PruebaWebMvc;
import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.api.VehiculoController;
import com.uade.crm.vehiculos.application.ActualizacionVehiculoService;
import com.uade.crm.vehiculos.application.AltaVehiculoService;
import com.uade.crm.vehiculos.application.ConsultaVehiculoService;
import com.uade.crm.vehiculos.application.FotoVehiculoService;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.port.FotoStorage;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

@PruebaWebMvc(controllers = VehiculoController.class)
@Import(SecretosEnLogsTest.StorageContraServidorFalso.class)
@ExtendWith(OutputCaptureExtension.class)
class SecretosEnLogsTest {

    private static final UUID ID = UUID.fromString("9e8d7c6b-5a4f-4e3d-8c1b-0a9f8e7d6c5b");
    private static final String OBJETOS = URL + "/storage/v1/object/" + BUCKET;
    private static final String PUBLICA_PNG = URL + "/storage/v1/object/public/" + BUCKET + "/" + ID + ".png";

    private static final List<String> LOGGERS = List.of("com.uade.crm.vehiculos", "org.springframework.web",
            "org.springframework.http", "org.springframework.test.web.servlet.TestDispatcherServlet");

    private final LoggingSystem logging = LoggingSystem.get(getClass().getClassLoader());

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MockRestServiceServer storage;

    @MockitoBean
    private VehiculoRepositorio repositorio;

    @MockitoBean
    private ConsultaVehiculoService consulta;

    @MockitoBean
    private AltaVehiculoService alta;

    @MockitoBean
    private ActualizacionVehiculoService actualizacion;

    @TestConfiguration
    static class StorageContraServidorFalso {

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();

        @Bean
        MockRestServiceServer servidor() {
            return servidor;
        }

        @Bean
        FotoStorage fotoStorage() {
            return new SupabaseFotoStorage(builder, SupabaseFotoStorageTest.propiedades(URL));
        }

        @Bean
        FotoVehiculoService fotoVehiculoService(VehiculoRepositorio repositorio, FotoStorage fotoStorage) {
            return new FotoVehiculoService(repositorio, fotoStorage, TransactionOperations.withoutTransaction());
        }
    }

    @BeforeEach
    void todoEnTrace() {
        storage.reset();
        LOGGERS.forEach(logger -> logging.setLogLevel(logger, LogLevel.TRACE));
    }

    @AfterEach
    void volverAlNivelConfigurado() {
        LOGGERS.forEach(logger -> logging.setLogLevel(logger, null));
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
    void unaFallaDeLaSubidaSeLogueaSinLasClaves(String caso, Consumer<ResponseActions> respuesta,
            CapturedOutput salida) throws Exception {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(unidad(null)));
        respuesta.accept(storage.expect(requestTo(OBJETOS + "/" + ID + ".png")));

        mvc.perform(foto(png(1024))).andExpect(status().isServiceUnavailable());

        storage.verify();
        sinClaves(salida);
    }

    @Test
    void unaFallaDelBorradoSeLogueaSinLasClaves(CapturedOutput salida) throws Exception {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(unidad(PUBLICA_PNG)));
        when(repositorio.buscarPorIdParaActualizar(ID)).thenReturn(Optional.of(unidad(PUBLICA_PNG)));
        when(repositorio.guardar(any()))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        storage.expect(requestTo(OBJETOS + "/" + ID + ".jpg")).andRespond(withSuccess());
        storage.expect(requestTo(OBJETOS)).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"error\":\"falla\"}"));

        mvc.perform(foto(jpeg(1024))).andExpect(status().isOk());

        storage.verify();
        sinClaves(salida);
    }

    private static void sinClaves(CapturedOutput salida) {
        assertThat(salida.getOut()).contains(" TRACE ").contains("Supabase Storage");
        assertThat(salida.getAll()).doesNotContain(CLAVE_SECRETA).doesNotContain(CLAVE_ESCRITURA);
    }

    private static RequestBuilder foto(byte[] contenido) {
        return multipart(HttpMethod.PUT, "/vehiculos/{id}/foto", ID)
                .file(new MockMultipartFile("archivo", "auto.png", "image/png", contenido))
                .header("X-Api-Key", CLAVE_ESCRITURA);
    }

    private static Vehiculo unidad(String fotoUrl) {
        return Vehiculo.builder(new RelojDePrueba())
                .id(ID)
                .marca("Toyota")
                .modelo("Corolla")
                .anio(2021)
                .kilometraje(45000)
                .precio(new BigDecimal("10000000"))
                .caja(Caja.AUTOMATICA)
                .fotoUrl(fotoUrl)
                .build();
    }
}
