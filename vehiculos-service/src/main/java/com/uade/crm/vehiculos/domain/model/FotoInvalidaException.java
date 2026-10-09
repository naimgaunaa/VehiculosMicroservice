package com.uade.crm.vehiculos.domain.model;

/** El archivo de la foto no es JPEG, PNG o WebP, está vacío o supera el tamaño máximo. */
public class FotoInvalidaException extends RuntimeException {

    public FotoInvalidaException(String message) {
        super(message);
    }
}
