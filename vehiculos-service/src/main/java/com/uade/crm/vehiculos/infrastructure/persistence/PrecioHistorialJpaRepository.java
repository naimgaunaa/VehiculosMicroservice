package com.uade.crm.vehiculos.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PrecioHistorialJpaRepository extends JpaRepository<PrecioHistorialEntity, UUID> {

    List<PrecioHistorialEntity> findByVehiculoIdOrderByVigenteDesdeDesc(UUID vehiculoId);

    /** La fila vigente: usa el índice {@code ix_precio_historial_vehiculo (vehiculo_id, vigente_desde DESC)}. */
    Optional<PrecioHistorialEntity> findFirstByVehiculoIdOrderByVigenteDesdeDesc(UUID vehiculoId);
}
