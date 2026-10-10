package com.uade.crm.vehiculos.api.seguridad;

public class ClaveServicioInvalidaException extends RuntimeException {

    public ClaveServicioInvalidaException() {
        super("Falta el header X-Api-Key o la clave no es válida");
    }
}
