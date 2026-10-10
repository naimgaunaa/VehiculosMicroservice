package com.uade.crm.vehiculos.api.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.uade.crm.vehiculos.domain.model.Caja;

public record AltaVehiculoRequest(
        @Schema(example = "Toyota") @NotBlank @Size(max = 60) String marca,
        @Schema(example = "Corolla") @NotBlank @Size(max = 80) String modelo,
        @Schema(example = "XEI CVT", nullable = true) @Size(max = 80) String version,
        @Schema(example = "2021") @NotNull @Min(1950) @Max(2027) Integer anio,
        @Schema(example = "45000") @NotNull @PositiveOrZero Integer kilometraje,
        @Schema(example = "10000000") @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal precio,
        @Schema(defaultValue = "true") Boolean disponible,
        @NotNull Caja caja) {

    public AltaVehiculoRequest {
        marca = recortar(marca);
        modelo = recortar(modelo);
        version = recortar(version);
        disponible = disponible == null ? Boolean.TRUE : disponible;
    }

    private static String recortar(String texto) {
        return texto == null ? null : texto.strip();
    }
}
