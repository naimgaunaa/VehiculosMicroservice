package com.uade.crm.vehiculos.domain.port;

import java.util.UUID;

import com.uade.crm.vehiculos.domain.model.ArchivoFoto;

public interface FotoStorage {

    String guardar(UUID vehiculoId, ArchivoFoto archivo);

    void eliminar(String fotoUrl);
}
