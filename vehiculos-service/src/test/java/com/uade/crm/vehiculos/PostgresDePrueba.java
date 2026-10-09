package com.uade.crm.vehiculos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL real en un contenedor para los tests de integración. Misma versión mayor que Supabase (17).
 * {@code @ServiceConnection} configura el DataSource solo, así que ningún test toca Supabase. Al ser un bean,
 * el contenedor se comparte entre las clases que comparten contexto.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresDePrueba {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }
}
