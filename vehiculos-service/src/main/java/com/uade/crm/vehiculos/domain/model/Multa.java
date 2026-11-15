package com.uade.crm.vehiculos.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Un acta de infracción asociada a la patente de una unidad, tal como la informó la consulta a la ANSV. */
public record Multa(
        String numeroActa,
        BigDecimal importe,
        BigDecimal importeSinDescuento,
        String tipoInfraccion,
        String lugar,
        String juzgado) {

    public Multa {
        Objects.requireNonNull(numeroActa, "El número de acta es obligatorio");
        Objects.requireNonNull(importe, "El importe es obligatorio");
    }

    /** {@code null} cuando el sitio no informa descuento para el acta. */
    public Optional<BigDecimal> descuento() {
        return Optional.ofNullable(importeSinDescuento);
    }
}
