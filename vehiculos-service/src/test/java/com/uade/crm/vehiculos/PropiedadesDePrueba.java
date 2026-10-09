package com.uade.crm.vehiculos;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Valores ficticios de {@code vehiculos.*} para los tests que levantan contexto Spring. Evita depender
 * de las variables {@code VEHICULOS_*} del shell ni del perfil {@code local} (que apunta a Supabase).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "vehiculos.modo-fotos=deshabilitado",
        "vehiculos.bucket-fotos=bucket-de-prueba",
        "vehiculos.clave-escritura=clave-ficticia-solo-para-tests-0123456789",
        // Vacías a propósito: si el desarrollador tiene VEHICULOS_SUPABASE_* cargadas en su shell,
        // el entorno no debe filtrarse a los tests.
        "vehiculos.supabase-url=",
        "vehiculos.supabase-clave-secreta="
})
public @interface PropiedadesDePrueba {
}
