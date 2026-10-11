package com.uade.crm.vehiculos.domain.model;

/** Se rompió una invariante de {@link Vehiculo}. */
public class VehiculoInvalidoException extends RuntimeException {

    public VehiculoInvalidoException(String message) {
        super(message);
    }
}
