package com.uade.crm.vehiculos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.uade.crm.vehiculos.PruebaWebMvc;
import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.application.ActualizacionVehiculoService;
import com.uade.crm.vehiculos.application.AltaVehiculoService;
import com.uade.crm.vehiculos.application.ConsultaVehiculoService;
import com.uade.crm.vehiculos.application.DatosInvalidosException;
import com.uade.crm.vehiculos.application.FotoVehiculoService;
import com.uade.crm.vehiculos.application.MultasVehiculoService;
import com.uade.crm.vehiculos.application.MultasVehiculoService.MultasDeVehiculo;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.Multa;
import com.uade.crm.vehiculos.domain.model.PatenteNoCoincideException;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoInvalidoException;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;

@PruebaWebMvc(controllers = VehiculoController.class)
class VehiculoControllerMultasTest {

    private static final String CLAVE = "clave-ficticia-solo-para-tests-0123456789";
    private static final UUID ID = UUID.fromString("9e8d7c6b-5a4f-4e3d-8c1b-0a9f8e7d6c5b");

    private static final String CUERPO = """
            {
              "dominio": "AB123CD",
              "infracciones": [
                {"numeroActa": "A-1", "dominio": "AB123CD", "importe": 33150, "importeSinDescuento": 39000,
                 "tipoInfraccion": "LEY 24449 - Art. 77", "lugar": "Córdoba", "juzgado": "Juzgado 1"},
                {"numeroActa": " A-2 ", "importe": 100.5, "importeSinDescuento": null}
              ],
              "subtotal": 39100.50,
              "mensaje": null
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultaVehiculoService consulta;

    @MockitoBean
    private AltaVehiculoService alta;

    @MockitoBean
    private ActualizacionVehiculoService actualizacion;

    @MockitoBean
    private FotoVehiculoService fotos;

    @MockitoBean
    private MultasVehiculoService multas;

    @Test
    void registrarMultasResponde204YPasaElResultadoAlServicio() throws Exception {
        mvc.perform(registrar(CUERPO)).andExpect(status().isNoContent());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Multa>> actas = ArgumentCaptor.forClass(List.class);
        verify(multas).registrar(eq(ID), eq("AB123CD"), eq(new BigDecimal("39100.50")), actas.capture());
        assertThat(actas.getValue()).hasSize(2);
        assertThat(actas.getValue().get(0)).isEqualTo(new Multa("A-1", new BigDecimal("33150"), new BigDecimal("39000"),
                "LEY 24449 - Art. 77", "Córdoba", "Juzgado 1"));
        // el número de acta se recorta y el importe sin descuento puede faltar
        assertThat(actas.getValue().get(1).numeroActa()).isEqualTo("A-2");
        assertThat(actas.getValue().get(1).importeSinDescuento()).isNull();
    }

    @Test
    void unaPatenteSinMultasEsUnResultadoValido() throws Exception {
        mvc.perform(registrar("{\"dominio\":\"AB123CD\",\"infracciones\":[],\"subtotal\":0,"
                + "\"mensaje\":\"Este auto no tiene multas registradas\"}")).andExpect(status().isNoContent());

        verify(multas).registrar(eq(ID), eq("AB123CD"), eq(BigDecimal.ZERO), eq(List.of()));
    }

    @Test
    void sinLaClaveDeServicioResponde401YNoLlegaAlServicio() throws Exception {
        mvc.perform(put("/vehiculos/{id}/multas", ID).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"));

        verifyNoInteractions(multas);
    }

    @Test
    void consultarLasMultasTambienExigeLaClave() throws Exception {
        mvc.perform(get("/vehiculos/{id}/multas", ID)).andExpect(status().isUnauthorized());

        verifyNoInteractions(multas);
    }

    @Test
    void unaUnidadInexistenteResponde404() throws Exception {
        doThrow(new VehiculoNoEncontradoException(ID)).when(multas).registrar(any(), any(), any(), any());

        mvc.perform(registrar(CUERPO)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VEHICULO_NO_ENCONTRADO"));
    }

    @Test
    void unaPatenteQueNoCoincideResponde409() throws Exception {
        doThrow(new PatenteNoCoincideException("Las multas son de otra patente")).when(multas)
                .registrar(any(), any(), any(), any());

        mvc.perform(registrar(CUERPO)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PATENTE_NO_COINCIDE"));
    }

    @Test
    void unDatoQueElDominioRechazaResponde422() throws Exception {
        doThrow(new DatosInvalidosException(new VehiculoInvalidoException("El número de acta A-1 está repetido")))
                .when(multas).registrar(any(), any(), any(), any());

        mvc.perform(registrar(CUERPO)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void sinLaListaDeInfraccionesResponde422() throws Exception {
        mvc.perform(registrar("{\"dominio\":\"AB123CD\",\"subtotal\":0}")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("infracciones"));

        verifyNoInteractions(multas);
    }

    @Test
    void sinSubtotalOConSubtotalNegativoResponde422() throws Exception {
        mvc.perform(registrar("{\"infracciones\":[]}")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("subtotal"));
        mvc.perform(registrar("{\"infracciones\":[],\"subtotal\":-1}")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("subtotal"));

        verifyNoInteractions(multas);
    }

    @Test
    void unActaSinNumeroOConImporteNegativoResponde422ConElCampo() throws Exception {
        mvc.perform(registrar("{\"infracciones\":[{\"numeroActa\":\" \",\"importe\":1}],\"subtotal\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("infracciones[0].numeroActa"));
        mvc.perform(registrar("{\"infracciones\":[{\"numeroActa\":\"A-1\",\"importe\":-5}],\"subtotal\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("infracciones[0].importe"));

        verifyNoInteractions(multas);
    }

    @Test
    void uncampoQueNoExisteSeRechaza() throws Exception {
        mvc.perform(registrar("{\"infracciones\":[],\"subtotal\":0,\"otro\":1}"))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(multas);
    }

    @Test
    void consultarDevuelveElSubtotalLaFechaYLasActas() throws Exception {
        Vehiculo vehiculo = unidad();
        vehiculo.registrarMultas(Instant.parse("2026-10-09T15:00:00Z"), new BigDecimal("39000"));
        when(multas.obtener(ID)).thenReturn(new MultasDeVehiculo(vehiculo, List.of(
                new Multa("A-1", new BigDecimal("33150"), new BigDecimal("39000"), "LEY 24449", "Córdoba", "Juzgado 1"),
                new Multa("A-2", new BigDecimal("100"), null, null, null, null))));

        mvc.perform(get("/vehiculos/{id}/multas", ID).header("X-Api-Key", CLAVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehiculoId").value(ID.toString()))
                .andExpect(jsonPath("$.patente").value("AB123CD"))
                .andExpect(jsonPath("$.consultadasEn").value("2026-10-09T15:00:00Z"))
                .andExpect(jsonPath("$.subtotal").value(39000.00))
                .andExpect(jsonPath("$.cantidad").value(2))
                .andExpect(jsonPath("$.infracciones[0].numeroActa").value("A-1"))
                .andExpect(jsonPath("$.infracciones[0].importe").value(33150.00))
                .andExpect(jsonPath("$.infracciones[0].importeSinDescuento").value(39000.00))
                .andExpect(jsonPath("$.infracciones[1].importeSinDescuento").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.infracciones[1].lugar").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void unaUnidadNuncaConsultadaDevuelveFechaYSubtotalNulosYSinActas() throws Exception {
        when(multas.obtener(ID)).thenReturn(new MultasDeVehiculo(unidad(), List.of()));

        mvc.perform(get("/vehiculos/{id}/multas", ID).header("X-Api-Key", CLAVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultadasEn").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.subtotal").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.cantidad").value(0))
                .andExpect(jsonPath("$.infracciones.length()").value(0));
    }

    @Test
    void consultarUnaUnidadInexistenteResponde404() throws Exception {
        when(multas.obtener(ID)).thenThrow(new VehiculoNoEncontradoException(ID));

        mvc.perform(get("/vehiculos/{id}/multas", ID).header("X-Api-Key", CLAVE)).andExpect(status().isNotFound());
    }

    @Test
    void elIdTieneQueSerUnUuid() throws Exception {
        mvc.perform(get("/vehiculos/{id}/multas", "no-es-un-uuid").header("X-Api-Key", CLAVE))
                .andExpect(status().isBadRequest());
    }

    private static MockHttpServletRequestBuilder registrar(String cuerpo) {
        return put("/vehiculos/{id}/multas", ID).header("X-Api-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private static Vehiculo unidad() {
        return Vehiculo.builder(new RelojDePrueba()).id(ID).marca("Toyota").modelo("Corolla").anio(2021)
                .kilometraje(45000).precio(new BigDecimal("10000000")).caja(Caja.MANUAL).patente("AB123CD").build();
    }
}
