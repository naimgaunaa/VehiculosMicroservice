package com.uade.crm.vehiculos.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de la documentación OpenAPI y el esquema de la clave de servicio de las escrituras.
 * El requisito de seguridad se aplica por operación, no de forma global: las lecturas son abiertas.
 */
@Configuration
public class OpenApiConfig {

    public static final String ESQUEMA_CLAVE_SERVICIO = "claveServicio";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Servicio de Vehículos")
                        .version("0.1.0")
                        .description("Inventario de la agencia de autos usados: unidades, precios, historial de precios y fotos."))
                .components(new Components().addSecuritySchemes(ESQUEMA_CLAVE_SERVICIO,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Api-Key")
                                .description("Clave de servicio. Solo la exigen las escrituras.")));
    }
}
