package com.uade.crm.vehiculos.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.uade.crm.vehiculos.PruebaWebMvc;

@PruebaWebMvc(controllers = ApiExceptionHandlerTest.ControladorDePrueba.class)
@Import(ApiExceptionHandlerTest.ControladorDePrueba.class)
class ApiExceptionHandlerTest {

    private static final String TYPE_BASE = "https://crm.uade.local/errores/";

    @Autowired
    private MockMvc mvc;

    @Test
    void validacionResponde422ConErroresPorCampo() throws Exception {
        ResultActions respuesta = mvc.perform(post("/prueba/validar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"marca\":\"  \",\"precio\":0}"));

        respuesta.andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + "validacion"))
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Hay campos que no cumplen las validaciones"))
                .andExpect(jsonPath("$.instance").value("/prueba/validar"))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.errores.length()").value(3))
                .andExpect(jsonPath("$.errores[?(@.campo=='marca')].mensaje").isNotEmpty())
                .andExpect(jsonPath("$.errores[?(@.campo=='caja')].mensaje").isNotEmpty())
                .andExpect(jsonPath("$.errores[?(@.campo=='precio')].mensaje").isNotEmpty());
    }

    @Test
    void enumFueraDeRangoResponde422ConLosValoresAceptados() throws Exception {
        mvc.perform(post("/prueba/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marca\":\"Ford\",\"caja\":\"Automática\",\"precio\":10}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("caja"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(containsString("AUTOMATICA, MANUAL")));
    }

    @Test
    void parametroConRestriccionResponde422() throws Exception {
        mvc.perform(get("/prueba/consulta").param("n", "0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("n"))
                .andExpect(jsonPath("$.errores[0].mensaje").isNotEmpty());
    }

    @Test
    void jsonMalFormadoResponde400() throws Exception {
        mvc.perform(post("/prueba/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marca\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + "json-invalido"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.errores").doesNotExist());
    }

    @Test
    void cuerpoAusenteResponde400() throws Exception {
        mvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
    }

    @Test
    void metodoNoPermitidoConservaElStatusDeSpring() throws Exception {
        mvc.perform(get("/prueba/validar"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + "solicitud-invalida"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void rutaInexistenteResponde404ConCodigoSolicitudInvalida() throws Exception {
        mvc.perform(get("/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/no-existe"))
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void tipoDeContenidoNoSoportadoResponde415() throws Exception {
        mvc.perform(post("/prueba/validar").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void idDeRutaQueNoEsUuidResponde400() throws Exception {
        mvc.perform(get("/prueba/vehiculos/no-es-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void errorNoControladoResponde500SinFiltrarElDetalle() throws Exception {
        String cuerpo = mvc.perform(get("/prueba/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + "error-interno"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("clave-de-la-base").doesNotContain("IllegalStateException");
    }

    @Test
    void archivoQueSuperaElLimiteDeSpringResponde422FotoInvalida() throws Exception {
        mvc.perform(get("/prueba/archivo-grande"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + "foto-invalida"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.codigo").value("FOTO_INVALIDA"))
                .andExpect(jsonPath("$.detail").value("La foto supera el tamaño máximo de 2 MB"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @RestController
    @RequestMapping("/prueba")
    static class ControladorDePrueba {

        public enum Caja {
            AUTOMATICA, MANUAL
        }

        public record Pedido(@NotBlank String marca, @NotNull Caja caja, @Positive BigDecimal precio) {
        }

        @PostMapping("/validar")
        void validar(@Valid @RequestBody Pedido pedido) {
        }

        @GetMapping("/consulta")
        void consulta(@RequestParam @Min(1) int n) {
        }

        @GetMapping("/vehiculos/{id}")
        void porId(@PathVariable UUID id) {
        }

        @GetMapping("/archivo-grande")
        void archivoGrande() {
            throw new MaxUploadSizeExceededException(3 * 1024 * 1024);
        }

        @GetMapping("/error")
        void error() {
            throw new IllegalStateException("clave-de-la-base");
        }
    }
}
