package com.uade.crm.vehiculos.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DominioConfig {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
