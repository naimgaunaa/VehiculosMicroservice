package com.uade.crm.vehiculos.api.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

public record ActualizacionVehiculoRequest(
        @Schema(example = "10500000", nullable = true) @Positive @Digits(integer = 12, fraction = 2) BigDecimal precio,
        @Schema(nullable = true) Boolean disponible) {

    @Schema(hidden = true)
    @AssertTrue(message = "hay que informar precio, disponible o ambos")
    public boolean isPrecioODisponible() {
        return precio != null || disponible != null;
    }
}
