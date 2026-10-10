package com.uade.crm.vehiculos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.PrecioHistorico;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

@ExtendWith(MockitoExtension.class)
class ConsultaVehiculoServiceTest {

    private static final UUID ID = UUID.fromString("9e8d7c6b-5a4f-4e3d-8c1b-0a9f8e7d6c5b");

    @Mock
    private VehiculoRepositorio repositorio;

    @InjectMocks
    private ConsultaVehiculoService servicio;

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {true, false})
    void listarPasaElFiltroTalCual(Boolean disponible) {
        List<Vehiculo> unidades = List.of(vehiculo());
        when(repositorio.buscar(disponible)).thenReturn(unidades);

        assertThat(servicio.listar(disponible)).isSameAs(unidades);
    }

    @Test
    void obtenerDevuelveLaUnidad() {
        Vehiculo vehiculo = vehiculo();
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(vehiculo));

        assertThat(servicio.obtener(ID)).isSameAs(vehiculo);
    }

    @Test
    void obtenerUnaUnidadInexistenteLanzaNoEncontrado() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(ID))
                .isInstanceOf(VehiculoNoEncontradoException.class)
                .hasMessageContaining(ID.toString());
    }

    @Test
    void historialDevuelveLoQueTraeElPuertoEnElMismoOrden() {
        List<PrecioHistorico> historial = List.of(
                new PrecioHistorico(new BigDecimal("10500000.00"), Instant.parse("2026-10-07T18:20:00Z")),
                new PrecioHistorico(new BigDecimal("10000000.00"), Instant.parse("2026-09-22T14:03:11Z")));
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(vehiculo()));
        when(repositorio.historialDePrecios(ID)).thenReturn(historial);

        assertThat(servicio.historialDePrecios(ID)).containsExactlyElementsOf(historial);
    }

    @Test
    void historialDeUnaUnidadInexistenteLanzaNoEncontradoSinConsultarElHistorial() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.historialDePrecios(ID)).isInstanceOf(VehiculoNoEncontradoException.class);
        verify(repositorio, never()).historialDePrecios(any());
    }

    private static Vehiculo vehiculo() {
        return Vehiculo.builder(new RelojDePrueba())
                .id(ID)
                .marca("Toyota")
                .modelo("Corolla")
                .anio(2021)
                .kilometraje(45000)
                .precio(new BigDecimal("10000000.00"))
                .caja(Caja.AUTOMATICA)
                .build();
    }
}
