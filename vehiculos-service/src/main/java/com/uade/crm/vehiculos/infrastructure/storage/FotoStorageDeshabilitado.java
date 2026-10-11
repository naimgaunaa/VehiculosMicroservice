package com.uade.crm.vehiculos.infrastructure.storage;

import java.util.UUID;

import com.uade.crm.vehiculos.domain.model.AlmacenamientoNoDisponibleException;
import com.uade.crm.vehiculos.domain.model.ArchivoFoto;
import com.uade.crm.vehiculos.domain.port.FotoStorage;

class FotoStorageDeshabilitado implements FotoStorage {

    private static final String MENSAJE = "El almacenamiento de fotos está deshabilitado";

    @Override
    public String guardar(UUID vehiculoId, ArchivoFoto archivo) {
        throw new AlmacenamientoNoDisponibleException(MENSAJE);
    }

    @Override
    public void eliminar(String fotoUrl) {
        throw new AlmacenamientoNoDisponibleException(MENSAJE);
    }
}
