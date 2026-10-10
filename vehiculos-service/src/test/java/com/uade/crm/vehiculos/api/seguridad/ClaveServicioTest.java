package com.uade.crm.vehiculos.api.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.uade.crm.vehiculos.PruebaWebMvc;
import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.api.VehiculoController;
import com.uade.crm.vehiculos.application.ActualizacionVehiculoService;
import com.uade.crm.vehiculos.application.AltaVehiculoService;
import com.uade.crm.vehiculos.application.ConsultaVehiculoService;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.Vehiculo;

@PruebaWebMvc(controllers = VehiculoController.class)
@ExtendWith(OutputCaptureExtension.class)
class ClaveServicioTest {

    private static final String CLAVE = "clave-ficticia-solo-para-tests-0123456789";
    private static final String CLAVE_INCORRECTA = "clave-ficticia-incorrecta-para-tests-9876543210";
    private static final UUID ID = UUID.fromString("9e8d7c6b-5a4f-4e3d-8c1b-0a9f8e7d6c5b");
    private static final String ALTA_VALIDA = """
            {"marca":"Toyota","modelo":"Corolla","anio":2021,"kilometraje":45000,"precio":10000000,"caja":"AUTOMATICA"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultaVehiculoService consulta;

    @MockitoBean
    private AltaVehiculoService alta;

    @MockitoBean
    private ActualizacionVehiculoService actualizacion;

    @Test
    void altaSinClaveResponde401() throws Exception {
        rechazada(mvc.perform(json(post("/vehiculos"), ALTA_VALIDA)));
        verifyNoInteractions(alta);
    }

    @ParameterizedTest
    @ValueSource(strings = {CLAVE_INCORRECTA, "clave-ficticia-solo-para-tests-012345678", "", " "})
    void altaConClaveIncorrectaResponde401(String clave) throws Exception {
        rechazada(mvc.perform(json(post("/vehiculos"), ALTA_VALIDA).header("X-Api-Key", clave)));
        verifyNoInteractions(alta);
    }

    @Test
    void altaConClaveCorrectaLlegaAlServicio() throws Exception {
        when(alta.registrar(any())).thenReturn(vehiculo());

        mvc.perform(json(post("/vehiculos"), ALTA_VALIDA).header("X-Api-Key", CLAVE))
                .andExpect(status().isCreated());
    }

    @Test
    void actualizacionSinClaveResponde401() throws Exception {
        rechazada(mvc.perform(json(put("/vehiculos/{id}", ID), "{\"precio\":10500000}")));
        verifyNoInteractions(actualizacion);
    }

    @Test
    void actualizacionConClaveIncorrectaResponde401() throws Exception {
        rechazada(mvc.perform(json(put("/vehiculos/{id}", ID), "{\"precio\":10500000}")
                .header("X-Api-Key", CLAVE_INCORRECTA)));
        verifyNoInteractions(actualizacion);
    }

    @Test
    void actualizacionConClaveCorrectaLlegaAlServicio() throws Exception {
        when(actualizacion.actualizar(ID, new BigDecimal("10500000"), null)).thenReturn(vehiculo());

        mvc.perform(json(put("/vehiculos/{id}", ID), "{\"precio\":10500000}").header("X-Api-Key", CLAVE))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"marca\":\"\",\"caja\":\"Automática\"}", "{\"marca\":", "", "{\"desconocido\":1}"})
    void elAltaSinClaveResponde401AunqueElCuerpoSeaInvalido(String cuerpo) throws Exception {
        rechazada(mvc.perform(json(post("/vehiculos"), cuerpo)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"precio\":0}", "{\"precio\":", "{\"marca\":\"Ford\"}"})
    void laActualizacionSinClaveResponde401AunqueElCuerpoSeaInvalido(String cuerpo) throws Exception {
        rechazada(mvc.perform(json(put("/vehiculos/{id}", ID), cuerpo)));
    }

    @Test
    void laActualizacionSinClaveResponde401AunqueElIdNoSeaUuid() throws Exception {
        rechazada(mvc.perform(json(put("/vehiculos/{id}", "no-es-un-uuid"), "{\"precio\":1}")));
    }

    @Test
    void lasLecturasSiguenAbiertas() throws Exception {
        when(consulta.listar(null)).thenReturn(List.of(vehiculo()));
        when(consulta.obtener(ID)).thenReturn(vehiculo());
        when(consulta.historialDePrecios(ID)).thenReturn(List.of());

        mvc.perform(get("/vehiculos")).andExpect(status().isOk());
        mvc.perform(get("/vehiculos/{id}", ID)).andExpect(status().isOk());
        mvc.perform(get("/vehiculos/{id}/precio", ID)).andExpect(status().isOk());
        mvc.perform(get("/vehiculos/{id}/historial-precios", ID)).andExpect(status().isOk());
    }

    @Test
    void niLaClaveConfiguradaNiLaEnviadaAparecenEnLogsNiRespuestas(CapturedOutput salida) throws Exception {
        when(alta.registrar(any())).thenReturn(vehiculo());
        StringBuilder respuestas = new StringBuilder();

        respuestas.append(cuerpo(mvc.perform(json(post("/vehiculos"), ALTA_VALIDA).header("X-Api-Key", CLAVE_INCORRECTA))));
        respuestas.append(cuerpo(mvc.perform(json(post("/vehiculos"), "{\"marca\":").header("X-Api-Key", CLAVE_INCORRECTA))));
        respuestas.append(cuerpo(mvc.perform(json(post("/vehiculos"), ALTA_VALIDA))));
        respuestas.append(cuerpo(mvc.perform(json(post("/vehiculos"), "{\"marca\":").header("X-Api-Key", CLAVE))));
        respuestas.append(cuerpo(mvc.perform(json(post("/vehiculos"), "{}").header("X-Api-Key", CLAVE))));
        respuestas.append(cuerpo(mvc.perform(json(post("/vehiculos"), ALTA_VALIDA).header("X-Api-Key", CLAVE))));

        assertThat(respuestas.toString()).doesNotContain(CLAVE).doesNotContain(CLAVE_INCORRECTA);
        assertThat(salida.getAll()).doesNotContain(CLAVE).doesNotContain(CLAVE_INCORRECTA);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder pedido, String cuerpo) {
        return pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static void rechazada(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://crm.uade.local/errores/clave-servicio-invalida"))
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.errores").doesNotExist());
    }

    private static String cuerpo(ResultActions respuesta) throws Exception {
        return respuesta.andReturn().getResponse().getContentAsString();
    }

    private static Vehiculo vehiculo() {
        return Vehiculo.builder(new RelojDePrueba())
                .id(ID)
                .marca("Toyota")
                .modelo("Corolla")
                .anio(2021)
                .kilometraje(45000)
                .precio(new BigDecimal("10000000"))
                .caja(Caja.AUTOMATICA)
                .build();
    }
}
