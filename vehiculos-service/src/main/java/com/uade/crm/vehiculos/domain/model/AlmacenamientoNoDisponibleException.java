package com.uade.crm.vehiculos.domain.model;

/** El almacenamiento de fotos no responde, rechazó la subida o está deshabilitado. */
public class AlmacenamientoNoDisponibleException extends RuntimeException {

    public AlmacenamientoNoDisponibleException(String message) {
        super(message);
    }

    public AlmacenamientoNoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
