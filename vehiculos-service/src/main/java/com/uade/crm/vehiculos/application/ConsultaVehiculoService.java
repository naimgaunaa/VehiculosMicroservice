package com.uade.crm.vehiculos.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.domain.model.PrecioHistorico;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

/** Lecturas del inventario: listado, detalle (y su precio) e historial de precios. */
@Service
@Transactional(readOnly = true)
public class ConsultaVehiculoService {

    private final VehiculoRepositorio repositorio;

    public ConsultaVehiculoService(VehiculoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * @param disponible {@code true} o {@code false} filtra por disponibilidad; {@code null} trae todas
     * @return las unidades, de la más nueva a la más vieja
     */
    public List<Vehiculo> listar(Boolean disponible) {
        return repositorio.buscar(disponible);
    }

    /** La unidad aunque no esté disponible: quien consulta decide qué hacer con {@code disponible}. */
    public Vehiculo obtener(UUID id) {
        return repositorio.buscarPorId(id).orElseThrow(() -> new VehiculoNoEncontradoException(id));
    }

    /** Precios que rigieron, del más nuevo al más viejo. Comprueba antes que la unidad exista. */
    public List<PrecioHistorico> historialDePrecios(UUID id) {
        obtener(id);
        return repositorio.historialDePrecios(id);
    }
}
