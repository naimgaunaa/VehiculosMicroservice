package com.uade.crm.vehiculos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VehiculosApplication {

    public static void main(String[] args) {
        SpringApplication.run(VehiculosApplication.class, args);
    }
}
