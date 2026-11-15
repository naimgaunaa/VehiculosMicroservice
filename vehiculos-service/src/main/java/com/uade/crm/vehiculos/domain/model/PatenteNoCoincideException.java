package com.uade.crm.vehiculos.domain.model;

/** Las multas informadas son de una patente distinta a la de la unidad (o la unidad todavía no tiene patente). */
public class PatenteNoCoincideException extends RuntimeException {

    public PatenteNoCoincideException(String mensaje) {
        super(mensaje);
    }
}
