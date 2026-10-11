package com.uade.crm.vehiculos;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.annotation.AliasFor;

/**
 * Test de controllers con MockMvc. Incluye el {@code ApiExceptionHandler} y el {@code CorrelationIdFilter},
 * y las propiedades ficticias de {@link PropiedadesDePrueba}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest
@PropiedadesDePrueba
public @interface PruebaWebMvc {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] controllers() default {};
}
