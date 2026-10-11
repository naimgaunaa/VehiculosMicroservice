package com.uade.crm.vehiculos.api.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formato de salida del dinero: siempre con 2 decimales. */
final class Precios {

    private Precios() {
    }

    /**
     * Fija la escala en 2 para que el JSON tenga siempre el mismo formato ({@code 10000000.00}). No redondea:
     * un valor con más de 2 decimales falla, porque la base ({@code NUMERIC(14,2)}) no puede devolverlo.
     */
    static BigDecimal aEscala2(BigDecimal precio) {
        return precio.setScale(2, RoundingMode.UNNECESSARY);
    }
}
