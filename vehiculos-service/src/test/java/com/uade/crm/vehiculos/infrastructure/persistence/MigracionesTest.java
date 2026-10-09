package com.uade.crm.vehiculos.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.PruebaIntegracion;

/**
 * La V1 contra PostgreSQL real. Que el contexto arranque ya prueba que las entidades coinciden con el
 * esquema ({@code ddl-auto: validate}); acá se verifica el esquema y sus restricciones.
 */
@PruebaIntegracion
@Transactional
class MigracionesTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAplicoSoloLaV1EnElSchemaVehiculos() {
        List<String> aplicadas = jdbc.queryForList(
                "SELECT version FROM vehiculos.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class);

        assertThat(aplicadas).containsExactly("1");
    }

    @Test
    void creaLasTablasYLosIndicesEnElSchemaVehiculos() {
        List<String> tablas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'vehiculos'",
                String.class);
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'vehiculos'", String.class);

        assertThat(tablas).contains("vehiculo", "precio_historial");
        assertThat(indices).contains("ix_vehiculo_disponible", "ix_precio_historial_vehiculo");
    }

    @Test
    void disponibleEsTruePorDefecto() {
        UUID id = insertarVehiculo("100000.00", 0, "AUTOMATICA");

        Boolean disponible = jdbc.queryForObject(
                "SELECT disponible FROM vehiculos.vehiculo WHERE id = ?", Boolean.class, id);

        assertThat(disponible).isTrue();
    }

    @Test
    void rechazaPrecioNoPositivo() {
        assertThatThrownBy(() -> insertarVehiculo("0", 0, "MANUAL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rechazaKilometrajeNegativo() {
        assertThatThrownBy(() -> insertarVehiculo("100000.00", -1, "MANUAL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rechazaCajaFueraDelEnum() {
        assertThatThrownBy(() -> insertarVehiculo("100000.00", 0, "SEMIAUTOMATICA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elHistorialExigeUnVehiculoExistente() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO vehiculos.precio_historial (id, vehiculo_id, precio, vigente_desde) "
                        + "VALUES (?, ?, 100000.00, now())", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elHistorialRechazaPrecioNoPositivo() {
        UUID id = insertarVehiculo("100000.00", 0, "MANUAL");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO vehiculos.precio_historial (id, vehiculo_id, precio, vigente_desde) "
                        + "VALUES (?, ?, 0, now())", UUID.randomUUID(), id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertarVehiculo(String precio, int kilometraje, String caja) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = new Timestamp(System.currentTimeMillis());
        jdbc.update("INSERT INTO vehiculos.vehiculo "
                        + "(id, marca, modelo, anio, kilometraje, precio, caja, creado_en, actualizado_en) "
                        + "VALUES (?, 'Marca', 'Modelo', 2020, ?, ?::numeric, ?, ?, ?)",
                id, kilometraje, precio, caja, ahora, ahora);
        return id;
    }
}
