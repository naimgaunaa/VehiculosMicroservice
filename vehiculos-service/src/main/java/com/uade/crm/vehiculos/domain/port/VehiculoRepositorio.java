package com.uade.crm.vehiculos.domain.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uade.crm.vehiculos.domain.model.PrecioHistorico;
import com.uade.crm.vehiculos.domain.model.Vehiculo;

/**
 * Persistencia de vehículos y de su historial de precios. No abre transacciones de negocio: las abre
 * el caso de uso, para que el alta o el cambio de precio y su fila de historial sean atómicos.
 */
public interface VehiculoRepositorio {

    /**
     * @param disponible {@code true} o {@code false} filtra por disponibilidad; {@code null} trae todos
     * @return las unidades, de la más nueva a la más vieja
     */
    List<Vehiculo> buscar(Boolean disponible);

    Optional<Vehiculo> buscarPorId(UUID id);

    /**
     * Inserta la unidad o actualiza la existente. La persistencia fija {@code creadoEn} en el alta y
     * {@code actualizadoEn} en cada llamada.
     */
    Vehiculo guardar(Vehiculo vehiculo);

    /** Agrega una fila al historial: {@code precio} rige desde {@code vigenteDesde}. */
    void registrarPrecio(UUID vehiculoId, BigDecimal precio, Instant vigenteDesde);

    /** Precios que rigieron para la unidad, del más nuevo al más viejo. Vacío si no tiene filas. */
    List<PrecioHistorico> historialDePrecios(UUID vehiculoId);
}
