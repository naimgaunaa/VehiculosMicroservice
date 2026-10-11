package com.uade.crm.vehiculos;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Test de integración con el contexto completo contra PostgreSQL real ({@link PostgresDePrueba}), con Flyway
 * aplicando las migraciones y las propiedades ficticias de {@link PropiedadesDePrueba}. Requiere Docker.
 * Las clases que comparten contexto comparten también la base: deben dejarla como la encontraron
 * (por ejemplo con {@code @Transactional}, que revierte al terminar cada test).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({PostgresDePrueba.class, RelojDePrueba.class, FotoStorageEnMemoria.class})
@PropiedadesDePrueba
public @interface PruebaIntegracion {
}
