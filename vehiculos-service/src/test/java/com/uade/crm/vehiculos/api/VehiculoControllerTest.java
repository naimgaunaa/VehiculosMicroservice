package com.uade.crm.vehiculos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.uade.crm.vehiculos.PruebaWebMvc;
import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.application.ActualizacionVehiculoService;
import com.uade.crm.vehiculos.application.AltaVehiculoService;
import com.uade.crm.vehiculos.application.ConsultaVehiculoService;
import com.uade.crm.vehiculos.application.FotoVehiculoService;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.PrecioHistorico;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoInvalidoException;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;

@PruebaWebMvc(controllers = VehiculoController.class)
class VehiculoControllerTest {

    private static final UUID ID_COROLLA = UUID.fromString("9e8d7c6b-5a4f-4e3d-8c1b-0a9f8e7d6c5b");
    private static final UUID ID_FIESTA = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
    private static final String TYPE_BASE = "https://crm.uade.local/errores/";

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

    @Test
    void listarSinFiltroDevuelveTodasLasUnidades() throws Exception {
        when(consulta.listar(null)).thenReturn(List.of(corolla(), fiesta()));

        mvc.perform(get("/vehiculos"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(ID_COROLLA.toString()))
                .andExpect(jsonPath("$[0].marca").value("Toyota"))
                .andExpect(jsonPath("$[0].modelo").value("Corolla"))
                .andExpect(jsonPath("$[0].version").value("XEI CVT"))
                .andExpect(jsonPath("$[0].anio").value(2021))
                .andExpect(jsonPath("$[0].kilometraje").value(45000))
                .andExpect(jsonPath("$[0].disponible").value(true))
                .andExpect(jsonPath("$[0].caja").value("AUTOMATICA"))
                .andExpect(jsonPath("$[0].fotoUrl").value("https://ejemplo.test/fotos/corolla.jpg"))
                .andExpect(jsonPath("$[1].modelo").value("Fiesta"))
                .andExpect(jsonPath("$[1].caja").value("MANUAL"));
    }

    @Test
    void listarConDisponibleTruePasaElFiltroAlServicio() throws Exception {
        when(consulta.listar(true)).thenReturn(List.of(corolla()));

        mvc.perform(get("/vehiculos").param("disponible", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].disponible").value(true));
        verify(consulta, never()).listar(null);
    }

    @Test
    void listarConDisponibleFalsePasaElFiltroAlServicio() throws Exception {
        when(consulta.listar(false)).thenReturn(List.of(fiesta()));

        mvc.perform(get("/vehiculos").param("disponible", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].disponible").value(false));
        verify(consulta, never()).listar(null);
    }

    @Test
    void listarConDisponibleQueNoEsBooleanoResponde400() throws Exception {
        mvc.perform(get("/vehiculos").param("disponible", "si"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void listaVaciaResponde200ConArregloVacio() throws Exception {
        when(consulta.listar(null)).thenReturn(List.of());

        mvc.perform(get("/vehiculos"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void obtenerDevuelveLaUnidadSinFechasInternas() throws Exception {
        when(consulta.obtener(ID_FIESTA)).thenReturn(fiesta());

        String cuerpo = mvc.perform(get("/vehiculos/{id}", ID_FIESTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID_FIESTA.toString()))
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.creadoEn").doesNotExist())
                .andExpect(jsonPath("$.actualizadoEn").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("creadoEn").doesNotContain("actualizadoEn");
    }

    @Test
    void versionYFotoAusentesSalenEnNull() throws Exception {
        when(consulta.obtener(ID_FIESTA)).thenReturn(fiesta());

        String cuerpo = mvc.perform(get("/vehiculos/{id}", ID_FIESTA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).contains("\"version\":null").contains("\"fotoUrl\":null");
    }

    @Test
    void elPrecioSaleComoNumeroConDosDecimales() throws Exception {
        when(consulta.obtener(ID_COROLLA)).thenReturn(corolla());

        String cuerpo = mvc.perform(get("/vehiculos/{id}", ID_COROLLA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // El dominio guarda 10000000 (escala 0); la API siempre responde con escala 2 y sin comillas.
        assertThat(cuerpo).contains("\"precio\":10000000.00");
    }

    @Test
    void precioDevuelveSoloPrecioYDisponible() throws Exception {
        when(consulta.obtener(ID_FIESTA)).thenReturn(fiesta());

        String cuerpo = mvc.perform(get("/vehiculos/{id}/precio", ID_FIESTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.disponible").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).isEqualTo("{\"precio\":9800000.50,\"disponible\":false}");
    }

    @Test
    void historialVaDelMasNuevoAlMasViejo() throws Exception {
        when(consulta.historialDePrecios(ID_COROLLA)).thenReturn(List.of(
                new PrecioHistorico(new BigDecimal("10500000.00"), Instant.parse("2026-10-07T18:20:00Z")),
                new PrecioHistorico(new BigDecimal("10000000.00"), Instant.parse("2026-09-22T14:03:11Z"))));

        mvc.perform(get("/vehiculos/{id}/historial-precios", ID_COROLLA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].precio").value(10500000.00))
                .andExpect(jsonPath("$[0].vigenteDesde").value("2026-10-07T18:20:00Z"))
                .andExpect(jsonPath("$[1].precio").value(10000000.00))
                .andExpect(jsonPath("$[1].vigenteDesde").value("2026-09-22T14:03:11Z"));
    }

    @Test
    void unidadInexistenteResponde404ConElProblemDetailCompleto() throws Exception {
        when(consulta.obtener(ID_COROLLA)).thenThrow(new VehiculoNoEncontradoException(ID_COROLLA));

        mvc.perform(get("/vehiculos/{id}", ID_COROLLA).header("X-Correlation-Id", "prueba-404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + "vehiculo-no-encontrado"))
                .andExpect(jsonPath("$.title").value("Vehículo no encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No existe un vehículo con id " + ID_COROLLA))
                .andExpect(jsonPath("$.instance").value("/vehiculos/" + ID_COROLLA))
                .andExpect(jsonPath("$.codigo").value("VEHICULO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.correlationId").value("prueba-404"))
                .andExpect(jsonPath("$.errores").doesNotExist());
    }

    @Test
    void precioDeUnidadInexistenteResponde404() throws Exception {
        when(consulta.obtener(ID_COROLLA)).thenThrow(new VehiculoNoEncontradoException(ID_COROLLA));

        mvc.perform(get("/vehiculos/{id}/precio", ID_COROLLA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VEHICULO_NO_ENCONTRADO"));
    }

    @Test
    void historialDeUnidadInexistenteResponde404() throws Exception {
        when(consulta.historialDePrecios(ID_COROLLA)).thenThrow(new VehiculoNoEncontradoException(ID_COROLLA));

        mvc.perform(get("/vehiculos/{id}/historial-precios", ID_COROLLA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VEHICULO_NO_ENCONTRADO"));
    }

    @Test
    void idQueNoEsUuidResponde400SinLlegarAlServicio() throws Exception {
        mvc.perform(get("/vehiculos/{id}", "no-es-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + "solicitud-invalida"))
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
        verify(consulta, never()).obtener(any());
    }

    /** Un dato guardado fuera de las invariantes es un error del servidor, no del cliente: 500 sin detalle. */
    @Test
    void datoGuardadoInvalidoResponde500SinDetalle() throws Exception {
        when(consulta.obtener(ID_COROLLA)).thenThrow(new VehiculoInvalidoException("El año debe estar entre 1950 y 2027"));

        String cuerpo = mvc.perform(get("/vehiculos/{id}", ID_COROLLA))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado"))
                .andExpect(jsonPath("$.errores").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("1950").doesNotContain("VehiculoInvalidoException");
    }

    private static Vehiculo corolla() {
        return Vehiculo.builder(new RelojDePrueba())
                .id(ID_COROLLA)
                .marca("Toyota")
                .modelo("Corolla")
                .version("XEI CVT")
                .anio(2021)
                .kilometraje(45000)
                .precio(new BigDecimal("10000000"))
                .disponible(true)
                .caja(Caja.AUTOMATICA)
                .fotoUrl("https://ejemplo.test/fotos/corolla.jpg")
                .build();
    }

    private static Vehiculo fiesta() {
        return Vehiculo.builder(new RelojDePrueba())
                .id(ID_FIESTA)
                .marca("Ford")
                .modelo("Fiesta")
                .anio(2015)
                .kilometraje(96000)
                .precio(new BigDecimal("9800000.5"))
                .disponible(false)
                .caja(Caja.MANUAL)
                .build();
    }
}
