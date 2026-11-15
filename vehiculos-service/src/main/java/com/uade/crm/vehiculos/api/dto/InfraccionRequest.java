package com.uade.crm.vehiculos.api.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Un acta tal como la informa el servicio {@code multas}. */
public record InfraccionRequest(
        @NotBlank @Size(max = 60) String numeroActa,
        @Schema(nullable = true, description = "La informa el scraper en cada acta; se ignora (vale la de la unidad)")
        @Size(max = 12) String dominio,
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal importe,
        @Schema(nullable = true, description = "null cuando el sitio no informa descuento")
        @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal importeSinDescuento,
        @Schema(nullable = true) @Size(max = 500) String tipoInfraccion,
        @Schema(nullable = true) @Size(max = 200) String lugar,
        @Schema(nullable = true) @Size(max = 200) String juzgado) {
}
