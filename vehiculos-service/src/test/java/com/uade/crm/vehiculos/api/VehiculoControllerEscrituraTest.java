package com.uade.crm.vehiculos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import com.uade.crm.vehiculos.PruebaWebMvc;
import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.application.ActualizacionVehiculoService;
import com.uade.crm.vehiculos.application.AltaVehiculo;
import com.uade.crm.vehiculos.application.AltaVehiculoService;
import com.uade.crm.vehiculos.application.ConsultaVehiculoService;
import com.uade.crm.vehiculos.application.FotoVehiculoService;
import com.uade.crm.vehiculos.application.DatosInvalidosException;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoInvalidoException;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;

@PruebaWebMvc(controllers = VehiculoController.class)
class VehiculoControllerEscrituraTest {

    private static final String CLAVE = "clave-ficticia-solo-para-tests-0123456789";
    private static final UUID ID = UUID.fromString("9e8d7c6b-5a4f-4e3d-8c1b-0a9f8e7d6c5b");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private ConsultaVehiculoService consulta;

    @MockitoBean
    private AltaVehiculoService alta;

    @MockitoBean
    private ActualizacionVehiculoService actualizacion;

    @MockitoBean
    private FotoVehiculoService fotos;

    @Test
    void altaValidaResponde201ConLocationYLaUnidad() throws Exception {
        when(alta.registrar(any())).thenReturn(corolla());

        mvc.perform(alta(altaValida()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/vehiculos/" + ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.marca").value("Toyota"))
                .andExpect(jsonPath("$.version").value("XEI CVT"))
                .andExpect(jsonPath("$.precio").value(10000000.00))
                .andExpect(jsonPath("$.caja").value("AUTOMATICA"))
                .andExpect(jsonPath("$.creadoEn").doesNotExist());
    }

    @Test
    void altaRecortaLosTextosYPasaTodosLosCamposAlServicio() throws Exception {
        when(alta.registrar(any())).thenReturn(corolla());
        Map<String, Object> cuerpo = altaValida();
        cuerpo.put("marca", "  Toyota ");
        cuerpo.put("modelo", "\tCorolla  ");
        cuerpo.put("version", " XEI CVT ");
        cuerpo.put("disponible", false);

        mvc.perform(alta(cuerpo)).andExpect(status().isCreated());

        assertThat(altaRecibida()).isEqualTo(new AltaVehiculo("Toyota", "Corolla", "XEI CVT", 2021, 45000,
                new BigDecimal("10000000"), false, Caja.AUTOMATICA));
    }

    @Test
    void altaSinDisponibleLaDejaDisponible() throws Exception {
        when(alta.registrar(any())).thenReturn(corolla());
        Map<String, Object> cuerpo = altaValida();
        cuerpo.remove("disponible");

        mvc.perform(alta(cuerpo)).andExpect(status().isCreated());

        assertThat(altaRecibida().disponible()).isTrue();
    }

    @Test
    void altaConDisponibleNuloLaDejaDisponible() throws Exception {
        when(alta.registrar(any())).thenReturn(corolla());
        Map<String, Object> cuerpo = altaValida();
        cuerpo.put("disponible", null);

        mvc.perform(alta(cuerpo)).andExpect(status().isCreated());

        assertThat(altaRecibida().disponible()).isTrue();
    }

    static Stream<Arguments> altasInvalidas() {
        return Stream.of(
                Arguments.of("marca", null),
                Arguments.of("marca", ""),
                Arguments.of("marca", "   "),
                Arguments.of("marca", "M".repeat(61)),
                Arguments.of("modelo", null),
                Arguments.of("modelo", " "),
                Arguments.of("modelo", "M".repeat(81)),
                Arguments.of("version", "V".repeat(81)),
                Arguments.of("anio", null),
                Arguments.of("anio", 1949),
                Arguments.of("anio", 2028),
                Arguments.of("kilometraje", null),
                Arguments.of("kilometraje", -1),
                Arguments.of("precio", null),
                Arguments.of("precio", 0),
                Arguments.of("precio", -1),
                Arguments.of("precio", new BigDecimal("100.001")),
                Arguments.of("precio", new BigDecimal("1000000000000")),
                Arguments.of("caja", null));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("altasInvalidas")
    void altaConUnCampoInvalidoResponde422ConEseCampo(String campo, Object valor) throws Exception {
        Map<String, Object> cuerpo = altaValida();
        cuerpo.put(campo, valor);

        validacion(mvc.perform(alta(cuerpo)))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").isNotEmpty());
        verifyNoInteractions(alta);
    }

    static Stream<Arguments> altasEnElLimite() {
        return Stream.of(
                Arguments.of("marca", "M".repeat(60)),
                Arguments.of("modelo", "M".repeat(80)),
                Arguments.of("version", "  " + "V".repeat(80) + "  "),
                Arguments.of("anio", 1950),
                Arguments.of("anio", 2027),
                Arguments.of("kilometraje", 0),
                Arguments.of("precio", new BigDecimal("0.01")),
                Arguments.of("precio", new BigDecimal("999999999999.99")));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("altasEnElLimite")
    void altaEnElLimiteSeAcepta(String campo, Object valor) throws Exception {
        when(alta.registrar(any())).thenReturn(corolla());
        Map<String, Object> cuerpo = altaValida();
        cuerpo.put(campo, valor);

        mvc.perform(alta(cuerpo)).andExpect(status().isCreated());
    }

    @Test
    void altaSinCuerpoInformadoDevuelveTodosLosCamposObligatorios() throws Exception {
        validacion(mvc.perform(alta("{}")))
                .andExpect(jsonPath("$.errores.length()").value(6))
                .andExpect(jsonPath("$.errores[?(@.campo=='marca')]").isNotEmpty())
                .andExpect(jsonPath("$.errores[?(@.campo=='modelo')]").isNotEmpty())
                .andExpect(jsonPath("$.errores[?(@.campo=='anio')]").isNotEmpty())
                .andExpect(jsonPath("$.errores[?(@.campo=='kilometraje')]").isNotEmpty())
                .andExpect(jsonPath("$.errores[?(@.campo=='precio')]").isNotEmpty())
                .andExpect(jsonPath("$.errores[?(@.campo=='caja')]").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Automática", "automatica", "CVT"})
    void altaConCajaFueraDelEnumResponde422ConLosValoresAceptados(String caja) throws Exception {
        Map<String, Object> cuerpo = altaValida();
        cuerpo.put("caja", caja);

        validacion(mvc.perform(alta(cuerpo)))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("caja"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(containsString("AUTOMATICA, MANUAL")));
        verifyNoInteractions(alta);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "fotoUrl", "creadoEn"})
    void altaConUnCampoNoAdmitidoResponde422ConEseCampo(String campo) throws Exception {
        Map<String, Object> cuerpo = altaValida();
        cuerpo.put(campo, "x");

        validacion(mvc.perform(alta(cuerpo)))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value("campo no admitido"));
        verifyNoInteractions(alta);
    }

    @Test
    void altaConJsonMalFormadoResponde400() throws Exception {
        mvc.perform(alta("{\"marca\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
    }

    @Test
    void altaQueElDominioRechazaResponde422ConSuMensaje() throws Exception {
        when(alta.registrar(any())).thenThrow(
                new DatosInvalidosException(new VehiculoInvalidoException("El año debe estar entre 1950 y 2027")));

        validacion(mvc.perform(alta(altaValida())))
                .andExpect(jsonPath("$.detail").value("El año debe estar entre 1950 y 2027"))
                .andExpect(jsonPath("$.instance").value("/vehiculos"));
    }

    @Test
    void actualizacionDelPrecioPasaSoloElPrecio() throws Exception {
        when(actualizacion.actualizar(ID, new BigDecimal("10500000"), null)).thenReturn(corolla());

        mvc.perform(actualizacion("{\"precio\":10500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
        verify(actualizacion).actualizar(ID, new BigDecimal("10500000"), null);
    }

    @Test
    void actualizacionDeLaDisponibilidadPasaSoloLaDisponibilidad() throws Exception {
        when(actualizacion.actualizar(ID, null, false)).thenReturn(corolla());

        mvc.perform(actualizacion("{\"disponible\":false}")).andExpect(status().isOk());
        verify(actualizacion).actualizar(ID, null, false);
    }

    @Test
    void actualizacionDeAmbosPasaAmbos() throws Exception {
        when(actualizacion.actualizar(ID, new BigDecimal("10500000.50"), true)).thenReturn(corolla());

        mvc.perform(actualizacion("{\"precio\":10500000.50,\"disponible\":true}")).andExpect(status().isOk());
        verify(actualizacion).actualizar(ID, new BigDecimal("10500000.50"), true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"precio\":null}", "{\"disponible\":null}", "{\"precio\":null,\"disponible\":null}"})
    void actualizacionSinPrecioNiDisponibleResponde422(String cuerpo) throws Exception {
        validacion(mvc.perform(actualizacion(cuerpo)))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("precioODisponible"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("hay que informar precio, disponible o ambos"));
        verifyNoInteractions(actualizacion);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.00", "100.001", "1000000000000"})
    void actualizacionConPrecioInvalidoResponde422(String precio) throws Exception {
        validacion(mvc.perform(actualizacion("{\"precio\":" + precio + "}")))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("precio"));
        verifyNoInteractions(actualizacion);
    }

    @ParameterizedTest
    @ValueSource(strings = {"marca", "anio", "fotoUrl", "id"})
    void actualizacionConUnCampoNoAdmitidoResponde422ConEseCampo(String campo) throws Exception {
        validacion(mvc.perform(actualizacion("{\"precio\":10500000,\"" + campo + "\":\"x\"}")))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value("campo no admitido"));
        verifyNoInteractions(actualizacion);
    }

    @Test
    void actualizacionConDisponibleQueNoEsBooleanoResponde422() throws Exception {
        validacion(mvc.perform(actualizacion("{\"disponible\":\"tal vez\"}")))
                .andExpect(jsonPath("$.errores[0].campo").value("disponible"));
    }

    @Test
    void actualizacionDeUnaUnidadInexistenteResponde404() throws Exception {
        when(actualizacion.actualizar(ID, new BigDecimal("10500000"), null))
                .thenThrow(new VehiculoNoEncontradoException(ID));

        mvc.perform(actualizacion("{\"precio\":10500000}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VEHICULO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void actualizacionQueElDominioRechazaResponde422() throws Exception {
        when(actualizacion.actualizar(ID, new BigDecimal("10500000"), null)).thenThrow(
                new DatosInvalidosException(new VehiculoInvalidoException("El precio debe ser mayor que cero")));

        validacion(mvc.perform(actualizacion("{\"precio\":10500000}")))
                .andExpect(jsonPath("$.detail").value("El precio debe ser mayor que cero"));
    }

    @Test
    void actualizacionDeUnDatoGuardadoInvalidoResponde500() throws Exception {
        when(actualizacion.actualizar(ID, new BigDecimal("10500000"), null))
                .thenThrow(new VehiculoInvalidoException("El año debe estar entre 1950 y 2027"));

        String cuerpo = mvc.perform(actualizacion("{\"precio\":10500000}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("1950");
    }

    @Test
    void actualizacionConJsonMalFormadoResponde400() throws Exception {
        mvc.perform(actualizacion("{\"precio\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
    }

    private RequestBuilder alta(Map<String, Object> cuerpo) throws Exception {
        return alta(json.writeValueAsString(cuerpo));
    }

    private static RequestBuilder alta(String cuerpo) {
        return post("/vehiculos").header("X-Api-Key", CLAVE).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static RequestBuilder actualizacion(String cuerpo) {
        return put("/vehiculos/{id}", ID).header("X-Api-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private static ResultActions validacion(ResultActions respuesta) throws Exception {
        return respuesta.andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    private AltaVehiculo altaRecibida() {
        ArgumentCaptor<AltaVehiculo> captor = ArgumentCaptor.forClass(AltaVehiculo.class);
        verify(alta).registrar(captor.capture());
        return captor.getValue();
    }

    private static Map<String, Object> altaValida() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("marca", "Toyota");
        cuerpo.put("modelo", "Corolla");
        cuerpo.put("version", "XEI CVT");
        cuerpo.put("anio", 2021);
        cuerpo.put("kilometraje", 45000);
        cuerpo.put("precio", new BigDecimal("10000000"));
        cuerpo.put("disponible", true);
        cuerpo.put("caja", "AUTOMATICA");
        return cuerpo;
    }

    private static Vehiculo corolla() {
        return Vehiculo.builder(new RelojDePrueba())
                .id(ID)
                .marca("Toyota")
                .modelo("Corolla")
                .version("XEI CVT")
                .anio(2021)
                .kilometraje(45000)
                .precio(new BigDecimal("10000000"))
                .caja(Caja.AUTOMATICA)
                .build();
    }
}
