package com.uade.crm.vehiculos.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.uade.crm.vehiculos.domain.model.PrecioHistorico;

/** Un precio que rigió para la unidad, desde {@code vigenteDesde}. */
public record PrecioHistoricoResponse(@Schema(example = "10000000.00") BigDecimal precio, Instant vigenteDesde) {

    public static PrecioHistoricoResponse de(PrecioHistorico historico) {
        return new PrecioHistoricoResponse(Precios.aEscala2(historico.precio()), historico.vigenteDesde());
    }
}
