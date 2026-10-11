package com.uade.crm.vehiculos;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL real en un contenedor para los tests de integración. Misma versión mayor que Supabase (17).
 * Al ser un bean, el contenedor se comparte entre las clases que comparten contexto.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresDePrueba {

    @Bean
    @ServiceConnection
    @Profile("!supabase")
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }

    @Bean
    @Profile("supabase")
    FlywayMigrationStrategy limpiarYMigrarSchemaDePrueba() {
        return flyway -> {
            GuardaSchemaDePrueba.exigirSchemaDeFlyway(
                    Arrays.asList(flyway.getConfiguration().getSchemas()), flyway.getConfiguration().getDefaultSchema());
            try (Connection conexion = flyway.getConfiguration().getDataSource().getConnection()) {
                conexion.isValid(5);
            } catch (SQLException e) {
                throw new IllegalStateException("No se pudo conectar a Supabase. Revisá que el proyecto no esté pausado "
                        + "y que VEHICULOS_DB_URL, VEHICULOS_DB_USUARIO y VEHICULOS_DB_CLAVE sean las del session pooler.", e);
            }
            flyway.clean();
            flyway.migrate();
        };
    }
}
