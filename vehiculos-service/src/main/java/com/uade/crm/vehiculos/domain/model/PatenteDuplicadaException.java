package com.uade.crm.vehiculos.domain.model;

/** Otra unidad ya tiene esa patente. */
public class PatenteDuplicadaException extends RuntimeException {

    public PatenteDuplicadaException(String patente) {
        super("Ya existe otra unidad con la patente " + patente);
    }
}
