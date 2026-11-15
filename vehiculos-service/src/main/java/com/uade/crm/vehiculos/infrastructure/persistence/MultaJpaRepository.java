package com.uade.crm.vehiculos.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface MultaJpaRepository extends JpaRepository<MultaEntity, UUID> {

    List<MultaEntity> findByVehiculoIdOrderByNumeroActaAsc(UUID vehiculoId);

    /** Borra en una sola sentencia y vacía el contexto: las filas nuevas pueden repetir el número de acta. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MultaEntity m where m.vehiculoId = :vehiculoId")
    void borrarDe(UUID vehiculoId);
}
