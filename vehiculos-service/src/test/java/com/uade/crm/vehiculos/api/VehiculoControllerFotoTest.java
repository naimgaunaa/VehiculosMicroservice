package com.uade.crm.vehiculos.api;

import static com.uade.crm.vehiculos.FotosDePrueba.png;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.uade.crm.vehiculos.PruebaWebMvc;
import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.application.ActualizacionVehiculoService;
import com.uade.crm.vehiculos.application.AltaVehiculoService;
import com.uade.crm.vehiculos.application.ConsultaVehiculoService;
import com.uade.crm.vehiculos.application.FotoVehiculoService;
import com.uade.crm.vehiculos.domain.model.AlmacenamientoNoDisponibleException;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.FotoInvalidaException;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;

@PruebaWebMvc(controllers = VehiculoController.class)
class VehiculoControllerFotoTest {

    private static final String CLAVE = "clave-ficticia-solo-para-tests-0123456789";
    private static final UUID ID = UUID.fromString("9e8d7c6b-5a4f-4e3d-8c1b-0a9f8e7d6c5b");
    private static final String URL = "https://proyecto-ficticio.supabase.co/storage/v1/object/public/vehiculos-fotos/"
            + ID + ".png";
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
    void unaFotoValidaResponde200ConLaUnidadYPasaLosBytesAlServicio() throws Exception {
        byte[] contenido = png(1536 * 1024);
        when(fotos.asignar(eq(ID), any())).thenReturn(corollaConFoto());

        mvc.perform(foto(contenido).header("X-Api-Key", CLAVE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.fotoUrl").value(URL));

        verify(fotos).asignar(ID, contenido);
    }

    @Test
    void sinClaveResponde401SinLlamarAlServicio() throws Exception {
        mvc.perform(foto(png(100)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"));

        verifyNoInteractions(fotos);
    }

    @Test
    void unaUnidadInexistenteResponde404() throws Exception {
        when(fotos.asignar(eq(ID), any())).thenThrow(new VehiculoNoEncontradoException(ID));

        mvc.perform(foto(png(100)).header("X-Api-Key", CLAVE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VEHICULO_NO_ENCONTRADO"));
    }

    @Test
    void unaFotoInvalidaResponde422FotoInvalida() throws Exception {
        when(fotos.asignar(eq(ID), any())).thenThrow(new FotoInvalidaException("La foto debe ser JPEG, PNG o WebP"));

        mvc.perform(foto(png(100)).header("X-Api-Key", CLAVE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + "foto-invalida"))
                .andExpect(jsonPath("$.codigo").value("FOTO_INVALIDA"))
                .andExpect(jsonPath("$.detail").value("La foto debe ser JPEG, PNG o WebP"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.errores").doesNotExist());
    }

    @Test
    void almacenamientoNoDisponibleResponde503() throws Exception {
        when(fotos.asignar(eq(ID), any()))
                .thenThrow(new AlmacenamientoNoDisponibleException("El almacenamiento de fotos está deshabilitado"));

        mvc.perform(foto(png(100)).header("X-Api-Key", CLAVE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + "almacenamiento-no-disponible"))
                .andExpect(jsonPath("$.codigo").value("ALMACENAMIENTO_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void unCuerpoQueNoEsMultipartResponde415() throws Exception {
        mvc.perform(put("/vehiculos/{id}/foto", ID).header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));

        verifyNoInteractions(fotos);
    }

    @Test
    void sinLaParteArchivoResponde400() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/vehiculos/{id}/foto", ID)
                        .file(new MockMultipartFile("otro", "auto.png", "image/png", png(100)))
                        .header("X-Api-Key", CLAVE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));

        verifyNoInteractions(fotos);
    }

    private static MockMultipartHttpServletRequestBuilder foto(byte[] contenido) {
        return multipart(HttpMethod.PUT, "/vehiculos/{id}/foto", ID)
                .file(new MockMultipartFile("archivo", "auto.png", "image/png", contenido));
    }

    private static Vehiculo corollaConFoto() {
        return Vehiculo.builder(new RelojDePrueba())
                .id(ID)
                .marca("Toyota")
                .modelo("Corolla")
                .anio(2021)
                .kilometraje(45000)
                .precio(new BigDecimal("10000000"))
                .caja(Caja.AUTOMATICA)
                .fotoUrl(URL)
                .build();
    }
}
