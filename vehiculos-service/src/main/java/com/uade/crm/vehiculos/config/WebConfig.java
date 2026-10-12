package com.uade.crm.vehiculos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.uade.crm.vehiculos.api.seguridad.ClaveServicioInterceptor;

@Configuration
@EnableConfigurationProperties(VehiculosProperties.class)
class WebConfig implements WebMvcConfigurer {

    private final VehiculosProperties propiedades;

    /** Orígenes permitidos para CORS, leídos de `cors.allowed-origins` (coma separados). */
    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    WebConfig(VehiculosProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ClaveServicioInterceptor(propiedades));
    }

    /** CORS para el frontend: solo los orígenes declarados en `cors.allowed-origins`. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("ETag", "Location")
                .allowCredentials(true);
    }
}
