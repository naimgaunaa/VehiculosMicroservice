package com.uade.crm.vehiculos.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoInvalidoException;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

@Service
public class AltaVehiculoService {

    private final VehiculoRepositorio repositorio;
    private final Clock clock;

    public AltaVehiculoService(VehiculoRepositorio repositorio, Clock clock) {
        this.repositorio = repositorio;
        this.clock = clock;
    }

    @Transactional
    public Vehiculo registrar(AltaVehiculo alta) {
        Vehiculo vehiculo = construir(alta);
        Instant ahora = clock.instant();
        Vehiculo guardado = repositorio.guardar(vehiculo);
        repositorio.registrarPrecio(guardado.id(), guardado.precio(), ahora);
        return guardado;
    }

    private Vehiculo construir(AltaVehiculo alta) {
        try {
            return Vehiculo.builder(clock)
                    .marca(alta.marca())
                    .modelo(alta.modelo())
                    .version(alta.version())
                    .anio(alta.anio())
                    .kilometraje(alta.kilometraje())
                    .precio(alta.precio())
                    .disponible(alta.disponible())
                    .caja(alta.caja())
                    .build();
        } catch (VehiculoInvalidoException e) {
            throw new DatosInvalidosException(e);
        }
    }
}
