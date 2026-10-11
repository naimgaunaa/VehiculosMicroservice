package com.uade.crm.vehiculos.application;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.uade.crm.vehiculos.domain.model.AlmacenamientoNoDisponibleException;
import com.uade.crm.vehiculos.domain.model.ArchivoFoto;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;
import com.uade.crm.vehiculos.domain.port.FotoStorage;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

@Service
public class FotoVehiculoService {

    private static final Logger log = LoggerFactory.getLogger(FotoVehiculoService.class);

    private final VehiculoRepositorio repositorio;
    private final FotoStorage storage;
    private final TransactionOperations transacciones;

    public FotoVehiculoService(VehiculoRepositorio repositorio, FotoStorage storage,
            TransactionOperations transacciones) {
        this.repositorio = repositorio;
        this.storage = storage;
        this.transacciones = transacciones;
    }

    public Vehiculo asignar(UUID id, byte[] contenido) {
        buscar(id);
        ArchivoFoto archivo = ArchivoFoto.de(contenido);
        String url = storage.guardar(id, archivo);
        Asignacion asignacion = transacciones.execute(estado -> asignarYGuardar(id, url));
        asignacion.anterior()
                .filter(anterior -> !anterior.equals(url))
                .ifPresent(this::eliminarAnterior);
        return asignacion.guardado();
    }

    private Asignacion asignarYGuardar(UUID id, String url) {
        Vehiculo vehiculo = repositorio.buscarPorIdParaActualizar(id)
                .orElseThrow(() -> new VehiculoNoEncontradoException(id));
        Optional<String> anterior = vehiculo.fotoUrl();
        vehiculo.asignarFoto(url);
        return new Asignacion(repositorio.guardar(vehiculo), anterior);
    }

    private void eliminarAnterior(String url) {
        try {
            storage.eliminar(url);
        } catch (AlmacenamientoNoDisponibleException e) {
            log.warn("No se pudo eliminar la foto anterior {}: {}", url, e.getMessage());
        }
    }

    private Vehiculo buscar(UUID id) {
        return repositorio.buscarPorId(id).orElseThrow(() -> new VehiculoNoEncontradoException(id));
    }

    private record Asignacion(Vehiculo guardado, Optional<String> anterior) {
    }
}
