package com.uade.crm.vehiculos.application;

import java.math.BigDecimal;

import com.uade.crm.vehiculos.domain.model.Caja;

public record AltaVehiculo(
        String marca,
        String modelo,
        String version,
        int anio,
        int kilometraje,
        BigDecimal precio,
        boolean disponible,
        Caja caja) {
}
