package com.uade.crm.vehiculos.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PrecioHistorialJpaRepository extends JpaRepository<PrecioHistorialEntity, UUID> {

    List<PrecioHistorialEntity> findByVehiculoIdOrderByVigenteDesdeDesc(UUID vehiculoId);
}
