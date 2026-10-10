package com.uade.crm.vehiculos.infrastructure.storage;

import com.uade.crm.vehiculos.domain.port.FotoStorage;

public final class FotoStorageDeshabilitadoDePrueba {

    private FotoStorageDeshabilitadoDePrueba() {
    }

    public static FotoStorage crear() {
        return new FotoStorageDeshabilitado();
    }
}
