package com.uade.crm.vehiculos.application;

import com.uade.crm.vehiculos.domain.model.VehiculoInvalidoException;

public class DatosInvalidosException extends RuntimeException {

    public DatosInvalidosException(VehiculoInvalidoException causa) {
        super(causa.getMessage(), causa);
    }
}
