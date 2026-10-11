package com.uade.crm.vehiculos.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

interface VehiculoJpaRepository extends JpaRepository<VehiculoEntity, UUID> {

    List<VehiculoEntity> findByDisponible(boolean disponible, Sort orden);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VehiculoEntity> findParaActualizarById(UUID id);
}
