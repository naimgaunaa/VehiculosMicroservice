package com.uade.crm.vehiculos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.uade.crm.vehiculos.PruebaWebMvc;

@PruebaWebMvc(controllers = {})
class CorrelationIdFilterTest {

    private static final String HEADER = "X-Correlation-Id";

    @Autowired
    private MockMvc mvc;

    @Test
    void conservaElHeaderEntranteEnLaRespuestaYEnElProblemDetail() throws Exception {
        mvc.perform(get("/no-existe").header(HEADER, "abc-123"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HEADER, "abc-123"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    void generaUnUuidSiNoVieneElHeader() throws Exception {
        MvcResult resultado = mvc.perform(get("/no-existe")).andReturn();

        String id = resultado.getResponse().getHeader(HEADER);
        assertThat(id).isNotNull();
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(resultado.getResponse().getContentAsString()).contains("\"correlationId\":\"" + id + "\"");
    }

    @Test
    void reemplazaUnHeaderConCaracteresInvalidos() throws Exception {
        String malo = "abc def\ninjected";
        MvcResult resultado = mvc.perform(get("/no-existe").header(HEADER, malo)).andReturn();

        String id = resultado.getResponse().getHeader(HEADER);
        assertThat(id).isNotEqualTo(malo);
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void reemplazaUnHeaderEnBlancoOMuyLargo() throws Exception {
        for (String invalido : new String[] {" ", "a".repeat(65)}) {
            String id = mvc.perform(get("/no-existe").header(HEADER, invalido)).andReturn()
                    .getResponse().getHeader(HEADER);
            assertThat(id).isNotEqualTo(invalido);
            assertThat(UUID.fromString(id)).isNotNull();
        }
    }

    @Test
    void limpiaElMdcAlTerminar() throws Exception {
        mvc.perform(get("/no-existe").header(HEADER, "abc-123"));

        assertThat(MDC.get("correlationId")).isNull();
    }
}
