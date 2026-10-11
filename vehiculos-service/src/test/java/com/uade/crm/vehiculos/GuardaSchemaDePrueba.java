package com.uade.crm.vehiculos;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

public class GuardaSchemaDePrueba implements EnvironmentPostProcessor, Ordered {

    public static final String SCHEMA_DE_PRUEBA = "vehiculos_test";

    private static final List<String> VARIABLES = List.of("VEHICULOS_DB_URL", "VEHICULOS_DB_USUARIO", "VEHICULOS_DB_CLAVE");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment entorno, SpringApplication aplicacion) {
        if (!entorno.acceptsProfiles(Profiles.of("supabase")) && !entorno.containsProperty("spring.datasource.url")) {
            return;
        }
        List<String> faltantes = VARIABLES.stream().filter(variable -> !StringUtils.hasText(entorno.getProperty(variable))).toList();
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException("La suite contra Supabase (-Psupabase) necesita las variables "
                    + String.join(", ", faltantes) + ". Cargalas con scripts/cargar-env.sh antes de correr Maven.");
        }
        exigirSchemaDeFlyway(
                Binder.get(entorno).bind("spring.flyway.schemas", Bindable.listOf(String.class)).orElse(List.of()),
                entorno.getProperty("spring.flyway.default-schema"));
        String hibernate = entorno.getProperty("spring.jpa.properties.hibernate.default_schema");
        if (!SCHEMA_DE_PRUEBA.equals(hibernate)) {
            throw new IllegalStateException("La suite contra una base real solo corre en el schema " + SCHEMA_DE_PRUEBA
                    + ", y spring.jpa.properties.hibernate.default_schema es " + hibernate + ".");
        }
    }

    public static void exigirSchemaDeFlyway(List<String> schemas, String defaultSchema) {
        if (!List.of(SCHEMA_DE_PRUEBA).equals(schemas)) {
            throw new IllegalStateException("La suite contra una base real solo corre en el schema " + SCHEMA_DE_PRUEBA
                    + ", y spring.flyway.schemas es " + schemas + ". Nunca se limpia ni se migra otro schema.");
        }
        if (defaultSchema != null && !SCHEMA_DE_PRUEBA.equals(defaultSchema)) {
            throw new IllegalStateException("La suite contra una base real solo corre en el schema " + SCHEMA_DE_PRUEBA
                    + ", y spring.flyway.default-schema es " + defaultSchema + ".");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
