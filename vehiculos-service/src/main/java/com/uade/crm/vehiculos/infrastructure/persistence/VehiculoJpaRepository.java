package com.uade.crm.vehiculos.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

interface VehiculoJpaRepository extends JpaRepository<VehiculoEntity, UUID> {

    List<VehiculoEntity> findByDisponible(boolean disponible, Sort orden);
}
