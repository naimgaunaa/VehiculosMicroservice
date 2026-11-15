package com.uade.crm.vehiculos.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.domain.model.PatenteDuplicadaException;
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
        return actualizar(id, precio, disponible, null);
    }

    /** Cambia los campos informados (los {@code null} no se tocan). Una patente nueva descarta las multas de la anterior. */
    @Transactional
    public Vehiculo actualizar(UUID id, BigDecimal precio, Boolean disponible, String patente) {
        Vehiculo vehiculo = repositorio.buscarPorIdParaActualizar(id)
                .orElseThrow(() -> new VehiculoNoEncontradoException(id));
        BigDecimal precioAnterior = vehiculo.precio();
        boolean disponibleAnterior = vehiculo.disponible();
        String patenteAnterior = vehiculo.patente().orElse(null);
        if (precio != null) {
            actualizarPrecio(vehiculo, precio);
        }
        if (disponible != null) {
            vehiculo.cambiarDisponibilidad(disponible);
        }
        if (patente != null) {
            asignarPatente(vehiculo, patente);
        }
        boolean cambioPrecio = vehiculo.precio().compareTo(precioAnterior) != 0;
        boolean cambioPatente = !vehiculo.patente().equals(java.util.Optional.ofNullable(patenteAnterior));
        if (cambioPatente && repositorio.existeOtraConPatente(vehiculo.patente().orElseThrow(), id)) {
            throw new PatenteDuplicadaException(vehiculo.patente().orElseThrow());
        }
        if (!cambioPrecio && !cambioPatente && vehiculo.disponible() == disponibleAnterior) {
            return vehiculo;
        }
        Instant ahora = clock.instant();
        Vehiculo guardado = repositorio.guardar(vehiculo);
        if (cambioPrecio) {
            repositorio.registrarPrecio(id, guardado.precio(), ahora);
        }
        if (cambioPatente) {
            // Las actas eran de la patente anterior.
            repositorio.reemplazarMultas(id, java.util.List.of());
        }
        return guardado;
    }

    private static void asignarPatente(Vehiculo vehiculo, String patente) {
        try {
            vehiculo.asignarPatente(patente);
        } catch (VehiculoInvalidoException e) {
            throw new DatosInvalidosException(e);
        }
    }

    private static void actualizarPrecio(Vehiculo vehiculo, BigDecimal precio) {
        try {
            vehiculo.actualizarPrecio(precio);
        } catch (VehiculoInvalidoException e) {
            throw new DatosInvalidosException(e);
        }
    }
}
