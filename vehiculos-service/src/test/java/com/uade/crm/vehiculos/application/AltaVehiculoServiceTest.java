package com.uade.crm.vehiculos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoInvalidoException;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

@ExtendWith(MockitoExtension.class)
class AltaVehiculoServiceTest {

    @Mock
    private VehiculoRepositorio repositorio;

    private AltaVehiculoService servicio;

    @BeforeEach
    void crearServicio() {
        servicio = new AltaVehiculoService(repositorio, new RelojDePrueba());
    }

    @Test
    void guardaLaUnidadYRegistraSuPrecioInicialEnEseOrden() {
        when(repositorio.guardar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        Vehiculo creado = servicio.registrar(alta(2021, "10000000"));

        ArgumentCaptor<Vehiculo> guardado = ArgumentCaptor.forClass(Vehiculo.class);
        InOrder orden = inOrder(repositorio);
        orden.verify(repositorio).guardar(guardado.capture());
        orden.verify(repositorio).registrarPrecio(eq(creado.id()),
                argThat(precio -> precio.compareTo(new BigDecimal("10000000")) == 0), eq(RelojDePrueba.INICIO));
        orden.verifyNoMoreInteractions();
        assertThat(guardado.getValue().marca()).isEqualTo("Toyota");
        assertThat(guardado.getValue().version()).contains("XEI CVT");
        assertThat(guardado.getValue().anio()).isEqualTo(2021);
        assertThat(guardado.getValue().kilometraje()).isEqualTo(45000);
        assertThat(guardado.getValue().disponible()).isFalse();
        assertThat(guardado.getValue().caja()).isEqualTo(Caja.MANUAL);
        assertThat(guardado.getValue().fotoUrl()).isEqualTo(Optional.empty());
    }

    @Test
    void devuelveLoQueGuardoElRepositorio() {
        Vehiculo persistido = Vehiculo.builder(new RelojDePrueba()).marca("Toyota").modelo("Corolla").anio(2021)
                .kilometraje(45000).precio(new BigDecimal("10000000.00")).caja(Caja.MANUAL).build();
        when(repositorio.guardar(any())).thenReturn(persistido);

        assertThat(servicio.registrar(alta(2021, "10000000"))).isSameAs(persistido);
    }

    @Test
    void unAnioQueElDominioRechazaLanzaDatosInvalidosSinTocarElRepositorio() {
        assertThatThrownBy(() -> servicio.registrar(alta(2028, "10000000")))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("El año debe estar entre 1950 y 2027")
                .hasCauseInstanceOf(VehiculoInvalidoException.class);
        verifyNoInteractions(repositorio);
    }

    @Test
    void unPrecioQueElDominioRechazaLanzaDatosInvalidos() {
        assertThatThrownBy(() -> servicio.registrar(alta(2021, "100.001")))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("decimales");
        verifyNoInteractions(repositorio);
    }

    private static AltaVehiculo alta(int anio, String precio) {
        return new AltaVehiculo("Toyota", "Corolla", "XEI CVT", anio, 45000, new BigDecimal(precio), false,
                Caja.MANUAL);
    }
}
