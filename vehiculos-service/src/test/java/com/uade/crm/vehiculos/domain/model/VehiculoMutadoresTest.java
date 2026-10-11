package com.uade.crm.vehiculos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class VehiculoMutadoresTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);

    private static Vehiculo vehiculo() {
        return Vehiculo.builder(RELOJ)
                .marca("Toyota")
                .modelo("Corolla")
                .anio(2021)
                .kilometraje(45000)
                .precio(new BigDecimal("10000000"))
                .caja(Caja.AUTOMATICA)
                .build();
    }

    @Test
    void actualizarPrecioAceptaElPrecioPositivoMinimo() {
        Vehiculo vehiculo = vehiculo();

        vehiculo.actualizarPrecio(new BigDecimal("0.01"));

        assertThat(vehiculo.precio()).isEqualByComparingTo("0.01");
    }

    @Test
    void actualizarPrecioRechazaNuloSinCambiarElPrecio() {
        Vehiculo vehiculo = vehiculo();

        assertThatThrownBy(() -> vehiculo.actualizarPrecio(null))
                .isInstanceOf(VehiculoInvalidoException.class)
                .hasMessage("El precio debe ser mayor que cero");
        assertThat(vehiculo.precio()).isEqualByComparingTo("10000000");
    }

    @Test
    void actualizarPrecioNormalizaADosDecimales() {
        Vehiculo vehiculo = vehiculo();

        vehiculo.actualizarPrecio(new BigDecimal("10500000"));

        assertThat(vehiculo.precio()).isEqualTo(new BigDecimal("10500000.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "100.555", "1000000000000"})
    void actualizarPrecioRechazaValoresInvalidosSinCambiarElPrecio(String precio) {
        Vehiculo vehiculo = vehiculo();

        assertThatThrownBy(() -> vehiculo.actualizarPrecio(new BigDecimal(precio)))
                .isInstanceOf(VehiculoInvalidoException.class);
        assertThat(vehiculo.precio()).isEqualByComparingTo("10000000");
    }

    @Test
    void cambiarDisponibilidadVaEnAmbosSentidos() {
        Vehiculo vehiculo = vehiculo();

        vehiculo.cambiarDisponibilidad(false);
        assertThat(vehiculo.disponible()).isFalse();

        vehiculo.cambiarDisponibilidad(true);
        assertThat(vehiculo.disponible()).isTrue();
    }

    @Test
    void asignarFotoGuardaLaUrl() {
        Vehiculo vehiculo = vehiculo();

        vehiculo.asignarFoto("https://ejemplo.test/foto.png");

        assertThat(vehiculo.fotoUrl()).contains("https://ejemplo.test/foto.png");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void asignarFotoRechazaUrlVaciaSinCambiarLaFoto(String url) {
        Vehiculo vehiculo = vehiculo();
        vehiculo.asignarFoto("https://ejemplo.test/foto.png");

        assertThatThrownBy(() -> vehiculo.asignarFoto(url))
                .isInstanceOf(VehiculoInvalidoException.class)
                .hasMessage("La URL de la foto no puede estar vacía");
        assertThat(vehiculo.fotoUrl()).contains("https://ejemplo.test/foto.png");
    }
}
