package com.uade.crm.vehiculos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExcepcionesDeDominioTest {

    @Test
    void vehiculoNoEncontradoInformaElId() {
        UUID id = UUID.fromString("9e8d7c6b-5a4f-4e3d-2c1b-0a9f8e7d6c5b");

        VehiculoNoEncontradoException excepcion = new VehiculoNoEncontradoException(id);

        assertThat(excepcion.id()).isEqualTo(id);
        assertThat(excepcion).hasMessage("No existe un vehículo con id 9e8d7c6b-5a4f-4e3d-2c1b-0a9f8e7d6c5b");
    }

    @Test
    void almacenamientoNoDisponibleConservaLaCausa() {
        RuntimeException causa = new RuntimeException("timeout");

        assertThat(new AlmacenamientoNoDisponibleException("Storage no responde", causa)).hasCause(causa);
    }

    @Test
    void precioHistoricoRechazaNulos() {
        assertThatThrownBy(() -> new PrecioHistorico(null, Instant.now())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PrecioHistorico(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void precioHistoricoExponePrecioYVigencia() {
        Instant desde = Instant.parse("2026-10-07T18:20:00Z");

        PrecioHistorico historico = new PrecioHistorico(new BigDecimal("10500000"), desde);

        assertThat(historico.precio()).isEqualByComparingTo("10500000");
        assertThat(historico.vigenteDesde()).isEqualTo(desde);
    }
}
