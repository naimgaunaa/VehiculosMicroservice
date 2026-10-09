package com.uade.crm.vehiculos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;

import com.uade.crm.vehiculos.config.VehiculosProperties.ModoFotos;

class VehiculosPropertiesTest {

    private static final String CLAVE_ESCRITURA = "clave-ficticia-solo-para-tests-0123456789";
    private static final String CLAVE_SUPABASE = "sb_secret_ficticia_para_tests";

    // Sin el entorno del sistema: un shell con VEHICULOS_* cargadas no debe alterar los resultados.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(contexto -> contexto.getEnvironment().getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
            .withUserConfiguration(Config.class);

    @Configuration
    @EnableConfigurationProperties(VehiculosProperties.class)
    static class Config {
    }

    private static String[] valoresCompletos() {
        return new String[] {
                "vehiculos.modo-fotos=supabase",
                "vehiculos.bucket-fotos=vehiculos-fotos",
                "vehiculos.supabase-url=https://proyecto-ficticio.supabase.co",
                "vehiculos.supabase-clave-secreta=" + CLAVE_SUPABASE,
                "vehiculos.clave-escritura=" + CLAVE_ESCRITURA
        };
    }

    private static String[] sin(String propiedad) {
        return Stream.of(valoresCompletos())
                .filter(valor -> !valor.startsWith("vehiculos." + propiedad + "="))
                .toArray(String[]::new);
    }

    private static String[] con(String... reemplazos) {
        return Stream.concat(Arrays.stream(valoresCompletos()), Arrays.stream(reemplazos)).toArray(String[]::new);
    }

    @Test
    void arrancaConTodosLosValores() {
        runner.withPropertyValues(valoresCompletos()).run(contexto -> {
            assertThat(contexto).hasNotFailed();
            VehiculosProperties props = contexto.getBean(VehiculosProperties.class);
            assertThat(props.modoFotos()).isEqualTo(ModoFotos.SUPABASE);
            assertThat(props.bucketFotos()).isEqualTo("vehiculos-fotos");
            assertThat(props.claveEscritura()).isEqualTo(CLAVE_ESCRITURA);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"modo-fotos", "bucket-fotos", "clave-escritura"})
    void noArrancaSiFaltaUnaObligatoria(String propiedad) {
        runner.withPropertyValues(sin(propiedad)).run(contexto -> assertThat(contexto).hasFailed());
    }

    @Test
    void noArrancaConUnaClaveDeEscrituraCorta() {
        runner.withPropertyValues(con("vehiculos.clave-escritura=corta"))
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).hasStackTraceContaining("VEHICULOS_CLAVE_ESCRITURA");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "vehiculos.supabase-url=https://<project-ref>.supabase.co",
            "vehiculos.clave-escritura=<clave-de-al-menos-32-caracteres-aaaa>",
            "vehiculos.bucket-fotos=${SIN_RESOLVER}"
    })
    void noArrancaSiQuedoUnPlaceholderSinCompletar(String valor) {
        runner.withPropertyValues(con(valor)).run(contexto -> assertThat(contexto).hasFailed());
    }

    @Test
    void enModoSupabaseExigeUrlYClaveSecreta() {
        runner.withPropertyValues(sin("supabase-url")).run(contexto -> {
            assertThat(contexto).hasFailed();
            assertThat(contexto.getStartupFailure()).hasStackTraceContaining("VEHICULOS_SUPABASE_URL");
        });
        runner.withPropertyValues(sin("supabase-clave-secreta")).run(contexto -> {
            assertThat(contexto).hasFailed();
            assertThat(contexto.getStartupFailure()).hasStackTraceContaining("VEHICULOS_SUPABASE_CLAVE_SECRETA");
        });
    }

    @Test
    void enModoDeshabilitadoNoExigeSupabase() {
        runner.withPropertyValues(
                "vehiculos.modo-fotos=deshabilitado",
                "vehiculos.bucket-fotos=vehiculos-fotos",
                "vehiculos.clave-escritura=" + CLAVE_ESCRITURA)
                .run(contexto -> assertThat(contexto).hasNotFailed());
    }

    @Test
    void toStringOcultaLosSecretos() {
        runner.withPropertyValues(valoresCompletos()).run(contexto -> {
            String texto = contexto.getBean(VehiculosProperties.class).toString();

            assertThat(texto).contains("****")
                    .doesNotContain(CLAVE_ESCRITURA)
                    .doesNotContain(CLAVE_SUPABASE)
                    .contains("vehiculos-fotos");
        });
    }
}
