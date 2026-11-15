package com.uade.crm.vehiculos.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.uade.crm.vehiculos.application.MultasVehiculoService.MultasDeVehiculo;
import com.uade.crm.vehiculos.domain.model.Multa;

/** Multas de la última consulta. Si la unidad nunca se consultó, {@code consultadasEn} y {@code subtotal} salen en {@code null}. */
public record MultasResponse(
        UUID vehiculoId,
        @Schema(example = "AB123CD", nullable = true) String patente,
        @Schema(nullable = true) Instant consultadasEn,
        @Schema(example = "39000.00", nullable = true) BigDecimal subtotal,
        int cantidad,
        List<InfraccionResponse> infracciones) {

    public record InfraccionResponse(
            String numeroActa,
            BigDecimal importe,
            @Schema(nullable = true) BigDecimal importeSinDescuento,
            @Schema(nullable = true) String tipoInfraccion,
            @Schema(nullable = true) String lugar,
            @Schema(nullable = true) String juzgado) {

        static InfraccionResponse de(Multa multa) {
            return new InfraccionResponse(multa.numeroActa(), Precios.aEscala2(multa.importe()),
                    multa.importeSinDescuento() == null ? null : Precios.aEscala2(multa.importeSinDescuento()),
                    multa.tipoInfraccion(), multa.lugar(), multa.juzgado());
        }
    }

    public static MultasResponse de(MultasDeVehiculo datos) {
        var vehiculo = datos.vehiculo();
        return new MultasResponse(
                vehiculo.id(),
                vehiculo.patente().orElse(null),
                vehiculo.multasConsultadasEn().orElse(null),
                vehiculo.multasSubtotal().map(Precios::aEscala2).orElse(null),
                datos.multas().size(),
                datos.multas().stream().map(InfraccionResponse::de).toList());
    }
}
