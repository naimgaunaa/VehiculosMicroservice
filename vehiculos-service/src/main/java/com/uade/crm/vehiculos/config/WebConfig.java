package com.uade.crm.vehiculos.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.uade.crm.vehiculos.api.seguridad.ClaveServicioInterceptor;

@Configuration
@EnableConfigurationProperties(VehiculosProperties.class)
class WebConfig implements WebMvcConfigurer {

    private final VehiculosProperties propiedades;

    WebConfig(VehiculosProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ClaveServicioInterceptor(propiedades));
    }
}
