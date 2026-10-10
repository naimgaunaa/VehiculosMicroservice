package com.uade.crm.vehiculos.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.Vehiculo;

/** Una unidad del inventario. {@code version} y {@code fotoUrl} salen en {@code null} si faltan. */
public record VehiculoResponse(
        UUID id,
        String marca,
        String modelo,
        @Schema(nullable = true) String version,
        int anio,
        int kilometraje,
        @Schema(example = "10000000.00") BigDecimal precio,
        boolean disponible,
        Caja caja,
        @Schema(nullable = true) String fotoUrl) {

    public static VehiculoResponse de(Vehiculo vehiculo) {
        return new VehiculoResponse(
                vehiculo.id(),
                vehiculo.marca(),
                vehiculo.modelo(),
                vehiculo.version().orElse(null),
                vehiculo.anio(),
                vehiculo.kilometraje(),
                Precios.aEscala2(vehiculo.precio()),
                vehiculo.disponible(),
                vehiculo.caja(),
                vehiculo.fotoUrl().orElse(null));
    }
}
