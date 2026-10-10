package com.uade.crm.vehiculos.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoInvalidoException;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

@Service
public class ActualizacionVehiculoService {

    private final VehiculoRepositorio repositorio;
    private final Clock clock;

    public ActualizacionVehiculoService(VehiculoRepositorio repositorio, Clock clock) {
        this.repositorio = repositorio;
        this.clock = clock;
    }

    @Transactional
    public Vehiculo actualizar(UUID id, BigDecimal precio, Boolean disponible) {
        Vehiculo vehiculo = repositorio.buscarPorId(id).orElseThrow(() -> new VehiculoNoEncontradoException(id));
        BigDecimal precioAnterior = vehiculo.precio();
        boolean disponibleAnterior = vehiculo.disponible();
        if (precio != null) {
            actualizarPrecio(vehiculo, precio);
        }
        if (disponible != null) {
            vehiculo.cambiarDisponibilidad(disponible);
        }
        boolean cambioPrecio = vehiculo.precio().compareTo(precioAnterior) != 0;
        if (!cambioPrecio && vehiculo.disponible() == disponibleAnterior) {
            return vehiculo;
        }
        Instant ahora = clock.instant();
        Vehiculo guardado = repositorio.guardar(vehiculo);
        if (cambioPrecio) {
            repositorio.registrarPrecio(id, guardado.precio(), ahora);
        }
        return guardado;
    }

    private static void actualizarPrecio(Vehiculo vehiculo, BigDecimal precio) {
        try {
            vehiculo.actualizarPrecio(precio);
        } catch (VehiculoInvalidoException e) {
            throw new DatosInvalidosException(e);
        }
    }
}
