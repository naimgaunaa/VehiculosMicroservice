package com.uade.crm.vehiculos.domain.model;

import java.util.UUID;

/** No existe un vehículo con el id pedido. */
public class VehiculoNoEncontradoException extends RuntimeException {

    private final UUID id;

    public VehiculoNoEncontradoException(UUID id) {
        super("No existe un vehículo con id " + id);
        this.id = id;
    }

    public UUID id() {
        return id;
    }
}
