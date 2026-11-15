package com.uade.crm.vehiculos.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de {@code multa} (migración {@code V2__patente_y_multas.sql}). Se reemplaza entera con cada consulta nueva.
 * El vehículo se referencia por id, sin relación JPA.
 */
@Entity
@Table(name = "multa")
class MultaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "vehiculo_id", nullable = false, updatable = false)
    private UUID vehiculoId;

    @Column(name = "numero_acta", nullable = false, length = 60, updatable = false)
    private String numeroActa;

    @Column(name = "importe", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal importe;

    @Column(name = "importe_sin_descuento", precision = 14, scale = 2, updatable = false)
    private BigDecimal importeSinDescuento;

    @Column(name = "tipo_infraccion", length = 500, updatable = false)
    private String tipoInfraccion;

    @Column(name = "lugar", length = 200, updatable = false)
    private String lugar;

    @Column(name = "juzgado", length = 200, updatable = false)
    private String juzgado;

    protected MultaEntity() {
    }

    MultaEntity(UUID id, UUID vehiculoId, String numeroActa, BigDecimal importe, BigDecimal importeSinDescuento,
            String tipoInfraccion, String lugar, String juzgado) {
        this.id = id;
        this.vehiculoId = vehiculoId;
        this.numeroActa = numeroActa;
        this.importe = importe;
        this.importeSinDescuento = importeSinDescuento;
        this.tipoInfraccion = tipoInfraccion;
        this.lugar = lugar;
        this.juzgado = juzgado;
    }

    String getNumeroActa() {
        return numeroActa;
    }

    BigDecimal getImporte() {
        return importe;
    }

    BigDecimal getImporteSinDescuento() {
        return importeSinDescuento;
    }

    String getTipoInfraccion() {
        return tipoInfraccion;
    }

    String getLugar() {
        return lugar;
    }

    String getJuzgado() {
        return juzgado;
    }

    @Override
    public String toString() {
        return "MultaEntity[id=" + id + "]";
    }
}
