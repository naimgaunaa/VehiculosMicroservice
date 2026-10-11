package com.uade.crm.vehiculos.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de {@code precio_historial}: un precio que empezó a regir en {@code vigenteDesde}. Solo de agregado.
 * El vehículo se referencia por id, sin relación JPA: el historial nunca se navega desde la entidad.
 */
@Entity
@Table(name = "precio_historial")
class PrecioHistorialEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "vehiculo_id", nullable = false, updatable = false)
    private UUID vehiculoId;

    @Column(name = "precio", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal precio;

    @Column(name = "vigente_desde", nullable = false, updatable = false)
    private Instant vigenteDesde;

    protected PrecioHistorialEntity() {
    }

    PrecioHistorialEntity(UUID id, UUID vehiculoId, BigDecimal precio, Instant vigenteDesde) {
        this.id = id;
        this.vehiculoId = vehiculoId;
        this.precio = precio;
        this.vigenteDesde = vigenteDesde;
    }

    UUID getId() {
        return id;
    }

    UUID getVehiculoId() {
        return vehiculoId;
    }

    BigDecimal getPrecio() {
        return precio;
    }

    Instant getVigenteDesde() {
        return vigenteDesde;
    }

    @Override
    public String toString() {
        return "PrecioHistorialEntity[id=" + id + "]";
    }
}
