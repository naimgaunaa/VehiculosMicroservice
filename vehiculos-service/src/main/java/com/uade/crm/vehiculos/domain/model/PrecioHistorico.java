package com.uade.crm.vehiculos.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Un precio que rigió para un vehículo, desde {@code vigenteDesde}. */
public record PrecioHistorico(BigDecimal precio, Instant vigenteDesde) {

    public PrecioHistorico {
        Objects.requireNonNull(precio, "El precio es obligatorio");
        Objects.requireNonNull(vigenteDesde, "La fecha de vigencia es obligatoria");
    }
}
