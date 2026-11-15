package com.uade.crm.vehiculos.api.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ActualizacionVehiculoRequest(
        @Schema(example = "10500000", nullable = true) @Positive @Digits(integer = 12, fraction = 2) BigDecimal precio,
        @Schema(nullable = true) Boolean disponible,
        @Schema(example = "AB123CD", nullable = true) @Size(max = 12) String patente) {

    @Schema(hidden = true)
    @AssertTrue(message = "hay que informar precio, disponible o patente")
    public boolean isPrecioODisponible() {
        return precio != null || disponible != null || patente != null;
    }
}
