package com.uade.crm.vehiculos.api.dto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Resultado de una consulta de multas, con la forma que publica el servicio {@code multas}
 * ({@code MultasConsultadas}) sin el {@code vehiculoId}: la unidad va en la URL.
 */
public record MultasRequest(
        @Schema(example = "AB123CD", nullable = true, description = "Patente consultada; si viene, tiene que ser la de la unidad")
        @Size(max = 12) String dominio,
        @Schema(description = "Actas encontradas; vacío si la patente no tiene multas")
        @NotNull @Size(max = 500) List<@NotNull @Valid InfraccionRequest> infracciones,
        @Schema(example = "39000.00", description = "Suma de las actas, sin descuento")
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal subtotal,
        @Schema(nullable = true, example = "Este auto no tiene multas registradas") @Size(max = 200) String mensaje) {
}
