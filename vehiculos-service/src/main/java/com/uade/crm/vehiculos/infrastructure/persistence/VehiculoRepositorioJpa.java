package com.uade.crm.vehiculos.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.domain.model.PrecioHistorico;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

/**
 * Adaptador del puerto sobre Spring Data. Fija {@code creadoEn} y {@code actualizadoEn} con el {@code Clock}
 * porque el dominio no los modela. Lee reconstruyendo con {@code Vehiculo.builder(clock)}, que revalida las
 * invariantes: un dato guardado fuera de ellas (por ejemplo un año fuera de rango) falla con
 * {@code VehiculoInvalidoException}.
 *
 * <p>Las escrituras exigen una transacción abierta ({@code MANDATORY}): la abre el caso de uso, para que el alta
 * o el cambio de precio y su fila de historial sean atómicos. Sin ella fallan con
 * {@code IllegalTransactionStateException} en vez de commitear cada una por separado.
 */
@Repository
class VehiculoRepositorioJpa implements VehiculoRepositorio {

    /** Más nuevo primero; el id desempata para que el orden sea estable. */
    private static final Sort ORDEN_LISTADO = Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.asc("id"));

    private final VehiculoJpaRepository vehiculos;
    private final PrecioHistorialJpaRepository historial;
    private final Clock clock;

    VehiculoRepositorioJpa(VehiculoJpaRepository vehiculos, PrecioHistorialJpaRepository historial, Clock clock) {
        this.vehiculos = vehiculos;
        this.historial = historial;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehiculo> buscar(Boolean disponible) {
        List<VehiculoEntity> entidades = disponible == null
                ? vehiculos.findAll(ORDEN_LISTADO)
                : vehiculos.findByDisponible(disponible, ORDEN_LISTADO);
        return entidades.stream().map(entidad -> aDominio(entidad, clock)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vehiculo> buscarPorId(UUID id) {
        return vehiculos.findById(id).map(entidad -> aDominio(entidad, clock));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Vehiculo guardar(Vehiculo vehiculo) {
        Instant ahora = clock.instant();
        VehiculoEntity entidad = vehiculos.findById(vehiculo.id()).orElseGet(() -> {
            VehiculoEntity nueva = new VehiculoEntity();
            nueva.setCreadoEn(ahora);
            return nueva;
        });
        aEntidad(vehiculo, entidad);
        entidad.setActualizadoEn(ahora);
        return aDominio(vehiculos.saveAndFlush(entidad), clock);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarPrecio(UUID vehiculoId, BigDecimal precio, Instant vigenteDesde) {
        // Con dos filas en el mismo instante el orden del historial quedaría indefinido y no se sabría cuál rige.
        historial.findFirstByVehiculoIdOrderByVigenteDesdeDesc(vehiculoId)
                .filter(ultima -> !vigenteDesde.isAfter(ultima.getVigenteDesde()))
                .ifPresent(ultima -> {
                    throw new IllegalStateException("El precio del vehículo " + vehiculoId + " no puede regir desde "
                            + vigenteDesde + ": la última fila del historial rige desde " + ultima.getVigenteDesde());
                });
        historial.saveAndFlush(new PrecioHistorialEntity(UUID.randomUUID(), vehiculoId, precio, vigenteDesde));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrecioHistorico> historialDePrecios(UUID vehiculoId) {
        return historial.findByVehiculoIdOrderByVigenteDesdeDesc(vehiculoId).stream()
                .map(fila -> new PrecioHistorico(fila.getPrecio(), fila.getVigenteDesde()))
                .toList();
    }

    /** Copia los campos del dominio sobre la entidad; no toca {@code creadoEn} ni {@code actualizadoEn}. */
    static void aEntidad(Vehiculo vehiculo, VehiculoEntity entidad) {
        entidad.setId(vehiculo.id());
        entidad.setMarca(vehiculo.marca());
        entidad.setModelo(vehiculo.modelo());
        entidad.setVersion(vehiculo.version().orElse(null));
        entidad.setAnio(vehiculo.anio());
        entidad.setKilometraje(vehiculo.kilometraje());
        entidad.setPrecio(vehiculo.precio());
        entidad.setDisponible(vehiculo.disponible());
        entidad.setCaja(vehiculo.caja());
        entidad.setFotoUrl(vehiculo.fotoUrl().orElse(null));
    }

    static Vehiculo aDominio(VehiculoEntity entidad, Clock clock) {
        return Vehiculo.builder(clock)
                .id(entidad.getId())
                .marca(entidad.getMarca())
                .modelo(entidad.getModelo())
                .version(entidad.getVersion())
                .anio(entidad.getAnio())
                .kilometraje(entidad.getKilometraje())
                .precio(entidad.getPrecio())
                .disponible(entidad.isDisponible())
                .caja(entidad.getCaja())
                .fotoUrl(entidad.getFotoUrl())
                .build();
    }
}
