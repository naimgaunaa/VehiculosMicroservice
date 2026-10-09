package com.uade.crm.vehiculos.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import com.uade.crm.vehiculos.domain.model.Caja;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Fila de {@code vehiculo} (migración {@code V1__vehiculo.sql}). Sin {@code @Version}: el último PUT gana.
 *
 * <p>Implementa {@link Persistable} porque el id lo asigna el dominio: sin eso Spring Data la toma como
 * existente y hace {@code merge}, que agrega un SELECT antes del INSERT. {@code getId} e {@code isNew} son
 * públicos porque los exige la interfaz; la clase sigue siendo de paquete.
 */
@Entity
@Table(name = "vehiculo")
class VehiculoEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "marca", nullable = false, length = 60)
    private String marca;

    @Column(name = "modelo", nullable = false, length = 80)
    private String modelo;

    @Column(name = "version", length = 80)
    private String version;

    @Column(name = "anio", nullable = false)
    private int anio;

    @Column(name = "kilometraje", nullable = false)
    private int kilometraje;

    @Column(name = "precio", nullable = false, precision = 14, scale = 2)
    private BigDecimal precio;

    @Column(name = "disponible", nullable = false)
    private boolean disponible;

    @Enumerated(EnumType.STRING)
    @Column(name = "caja", nullable = false, length = 15)
    private Caja caja;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Solo en memoria: {@code true} desde {@link #nueva(Instant)} hasta que Hibernate la inserta. */
    @Transient
    private boolean nueva;

    protected VehiculoEntity() {
    }

    /** Entidad para un alta: se inserta con {@code persist} y fija {@code creadoEn}. */
    static VehiculoEntity nueva(Instant creadoEn) {
        VehiculoEntity entidad = new VehiculoEntity();
        entidad.nueva = true;
        entidad.creadoEn = creadoEn;
        return entidad;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostPersist
    @PostLoad
    void marcarPersistida() {
        nueva = false;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getMarca() {
        return marca;
    }

    void setMarca(String marca) {
        this.marca = marca;
    }

    String getModelo() {
        return modelo;
    }

    void setModelo(String modelo) {
        this.modelo = modelo;
    }

    String getVersion() {
        return version;
    }

    void setVersion(String version) {
        this.version = version;
    }

    int getAnio() {
        return anio;
    }

    void setAnio(int anio) {
        this.anio = anio;
    }

    int getKilometraje() {
        return kilometraje;
    }

    void setKilometraje(int kilometraje) {
        this.kilometraje = kilometraje;
    }

    BigDecimal getPrecio() {
        return precio;
    }

    void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    boolean isDisponible() {
        return disponible;
    }

    void setDisponible(boolean disponible) {
        this.disponible = disponible;
    }

    Caja getCaja() {
        return caja;
    }

    void setCaja(Caja caja) {
        this.caja = caja;
    }

    String getFotoUrl() {
        return fotoUrl;
    }

    void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }

    Instant getActualizadoEn() {
        return actualizadoEn;
    }

    void setActualizadoEn(Instant actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }

    @Override
    public String toString() {
        return "VehiculoEntity[id=" + id + "]";
    }
}
