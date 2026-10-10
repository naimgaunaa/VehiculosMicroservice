package com.uade.crm.vehiculos.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.client.RestClient;

import com.uade.crm.vehiculos.config.VehiculosProperties;
import com.uade.crm.vehiculos.domain.port.FotoStorage;

class FotoStorageConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(contexto -> contexto.getEnvironment().getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(Propiedades.class, FotoStorageConfig.class)
            .withPropertyValues(
                    "vehiculos.bucket-fotos=vehiculos-fotos",
                    "vehiculos.supabase-url=https://proyecto-ficticio.supabase.co",
                    "vehiculos.supabase-clave-secreta=sb_secret_ficticia_para_tests",
                    "vehiculos.clave-escritura=clave-ficticia-solo-para-tests-0123456789");

    @Configuration
    @EnableConfigurationProperties(VehiculosProperties.class)
    static class Propiedades {
    }

    @ParameterizedTest
    @ValueSource(strings = {"supabase", "SUPABASE", "Supabase"})
    void elModoSupabaseUsaSupabaseFotoStorage(String modo) {
        runner.withPropertyValues("vehiculos.modo-fotos=" + modo).run(contexto -> {
            assertThat(contexto).hasNotFailed();
            assertThat(contexto).getBean(FotoStorage.class).isInstanceOf(SupabaseFotoStorage.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"deshabilitado", "DESHABILITADO"})
    void elModoDeshabilitadoUsaFotoStorageDeshabilitado(String modo) {
        runner.withPropertyValues("vehiculos.modo-fotos=" + modo, "vehiculos.supabase-url=",
                "vehiculos.supabase-clave-secreta=").run(contexto -> {
            assertThat(contexto).hasNotFailed();
            assertThat(contexto).getBean(FotoStorage.class).isInstanceOf(FotoStorageDeshabilitado.class);
        });
    }
}
