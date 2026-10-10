package com.uade.crm.vehiculos.api.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import com.uade.crm.vehiculos.domain.model.Vehiculo;

/** Precio actual de una unidad y si sigue disponible. */
public record PrecioResponse(@Schema(example = "10000000.00") BigDecimal precio, boolean disponible) {

    public static PrecioResponse de(Vehiculo vehiculo) {
        return new PrecioResponse(Precios.aEscala2(vehiculo.precio()), vehiculo.disponible());
    }
}
