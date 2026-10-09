package com.uade.crm.vehiculos.config;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DominioConfig {

    /**
     * Reloj UTC con resolución de microsegundos, la misma que {@code TIMESTAMPTZ}. Con {@code systemUTC()} solo,
     * un instante con nanosegundos se guardaría redondeado y al leerlo no sería igual al escrito.
     */
    @Bean
    Clock reloj() {
        return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
    }
}
