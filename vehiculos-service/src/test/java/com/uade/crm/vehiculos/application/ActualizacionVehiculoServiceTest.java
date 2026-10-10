package com.uade.crm.vehiculos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoInvalidoException;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

@ExtendWith(MockitoExtension.class)
class ActualizacionVehiculoServiceTest {

    private static final UUID ID = UUID.fromString("9e8d7c6b-5a4f-4e3d-8c1b-0a9f8e7d6c5b");

    @Mock
    private VehiculoRepositorio repositorio;

    private ActualizacionVehiculoService servicio;

    @BeforeEach
    void crearServicio() {
        servicio = new ActualizacionVehiculoService(repositorio, new RelojDePrueba());
    }

    @Test
    void unaUnidadInexistenteLanzaNoEncontradoSinGuardar() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(ID, new BigDecimal("10500000"), null))
                .isInstanceOf(VehiculoNoEncontradoException.class);
        verify(repositorio, never()).guardar(any());
        verify(repositorio, never()).registrarPrecio(any(), any(), any());
    }

    @Test
    void unPrecioDistintoGuardaYRegistraElPrecioNuevo() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(vehiculo("10000000.00", true)));
        when(repositorio.guardar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        Vehiculo actualizado = servicio.actualizar(ID, new BigDecimal("10500000"), null);

        assertThat(actualizado.precio()).isEqualByComparingTo("10500000");
        assertThat(actualizado.disponible()).isTrue();
        InOrder orden = inOrder(repositorio);
        orden.verify(repositorio).guardar(argThat(v -> v.precio().compareTo(new BigDecimal("10500000")) == 0));
        orden.verify(repositorio).registrarPrecio(eq(ID),
                argThat(precio -> precio.compareTo(new BigDecimal("10500000")) == 0), eq(RelojDePrueba.INICIO));
    }

    @Test
    void elPrecioQueSeRegistraEsElQueQuedoGuardado() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(vehiculo("10000000.00", true)));
        when(repositorio.guardar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.actualizar(ID, new BigDecimal("10500000.5"), null);

        ArgumentCaptor<BigDecimal> registrado = ArgumentCaptor.forClass(BigDecimal.class);
        verify(repositorio).registrarPrecio(eq(ID), registrado.capture(), eq(RelojDePrueba.INICIO));
        assertThat(registrado.getValue()).isEqualTo(new BigDecimal("10500000.50"));
    }

    @Test
    void soloDisponibleGuardaSinRegistrarPrecio() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(vehiculo("10000000.00", true)));
        when(repositorio.guardar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        Vehiculo actualizado = servicio.actualizar(ID, null, false);

        assertThat(actualizado.disponible()).isFalse();
        verify(repositorio).guardar(argThat(v -> !v.disponible()));
        verify(repositorio, never()).registrarPrecio(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"10500000", "10500000.0", "10500000.00", "10500000.000"})
    void elMismoPrecioConOtraEscalaNoGuardaNiRegistra(String precio) {
        Vehiculo actual = vehiculo("10500000.00", true);
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(actual));

        Vehiculo resultado = servicio.actualizar(ID, new BigDecimal(precio), null);

        assertThat(resultado.precio()).isEqualByComparingTo("10500000");
        verify(repositorio, never()).guardar(any());
        verify(repositorio, never()).registrarPrecio(any(), any(), any());
    }

    @Test
    void laMismaDisponibilidadNoGuarda() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(vehiculo("10500000.00", false)));

        servicio.actualizar(ID, null, false);

        verify(repositorio, never()).guardar(any());
        verify(repositorio, never()).registrarPrecio(any(), any(), any());
    }

    @Test
    void elMismoPrecioYOtraDisponibilidadGuardaSinRegistrarPrecio() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(vehiculo("10500000.00", true)));
        when(repositorio.guardar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.actualizar(ID, new BigDecimal("10500000"), false);

        verify(repositorio).guardar(argThat(v -> !v.disponible()));
        verify(repositorio, never()).registrarPrecio(any(), any(), any());
    }

    @Test
    void otroPrecioYOtraDisponibilidadGuardaUnaVezYRegistraElPrecio() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(vehiculo("10000000.00", true)));
        when(repositorio.guardar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.actualizar(ID, new BigDecimal("9000000"), false);

        verify(repositorio).guardar(argThat(v -> !v.disponible() && v.precio().compareTo(new BigDecimal("9000000")) == 0));
        verify(repositorio).registrarPrecio(eq(ID), any(), eq(RelojDePrueba.INICIO));
    }

    @Test
    void unPrecioQueElDominioRechazaLanzaDatosInvalidosSinGuardar() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(vehiculo("10000000.00", true)));

        assertThatThrownBy(() -> servicio.actualizar(ID, new BigDecimal("100.001"), null))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("decimales")
                .hasCauseInstanceOf(VehiculoInvalidoException.class);
        verify(repositorio, never()).guardar(any());
        verify(repositorio, never()).registrarPrecio(any(), any(), any());
    }

    @Test
    void unDatoGuardadoInvalidoNoSeConvierteEnDatosInvalidos() {
        when(repositorio.buscarPorId(ID)).thenThrow(new VehiculoInvalidoException("El año debe estar entre 1950 y 2027"));

        assertThatThrownBy(() -> servicio.actualizar(ID, new BigDecimal("10500000"), null))
                .isExactlyInstanceOf(VehiculoInvalidoException.class);
    }

    private static Vehiculo vehiculo(String precio, boolean disponible) {
        return Vehiculo.builder(new RelojDePrueba())
                .id(ID)
                .marca("Toyota")
                .modelo("Corolla")
                .anio(2021)
                .kilometraje(45000)
                .precio(new BigDecimal(precio))
                .disponible(disponible)
                .caja(Caja.AUTOMATICA)
                .build();
    }
}
