package com.uade.crm.vehiculos;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

class GuardaSchemaDePruebaTest {

    private final GuardaSchemaDePrueba guarda = new GuardaSchemaDePrueba();

    private static MockEnvironment supabase(String schemaFlyway, String schemaHibernate) {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("VEHICULOS_DB_URL", "jdbc:postgresql://localhost:1/ficticia")
                .withProperty("VEHICULOS_DB_USUARIO", "usuario_ficticio")
                .withProperty("VEHICULOS_DB_CLAVE", "clave_ficticia")
                .withProperty("spring.flyway.schemas", schemaFlyway)
                .withProperty("spring.jpa.properties.hibernate.default_schema", schemaHibernate);
        entorno.setActiveProfiles("test", "supabase");
        return entorno;
    }

    private void aplicar(MockEnvironment entorno) {
        guarda.postProcessEnvironment(entorno, null);
    }

    @Test
    void aceptaElSchemaDePrueba() {
        assertThatCode(() -> aplicar(supabase("vehiculos_test", "vehiculos_test"))).doesNotThrowAnyException();
    }

    @Test
    void rechazaElSchemaVehiculos() {
        assertThatThrownBy(() -> aplicar(supabase("vehiculos", "vehiculos")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vehiculos_test")
                .hasMessageContaining("[vehiculos]");
    }

    @Test
    void rechazaVariosSchemasAunqueIncluyanElDePrueba() {
        assertThatThrownBy(() -> aplicar(supabase("vehiculos_test,vehiculos", "vehiculos_test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vehiculos_test");
    }

    @Test
    void rechazaUnDefaultSchemaDeHibernateDistinto() {
        assertThatThrownBy(() -> aplicar(supabase("vehiculos_test", "vehiculos")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hibernate");
    }

    @Test
    void rechazaUnDefaultSchemaDeFlywayDistinto() {
        MockEnvironment entorno = supabase("vehiculos_test", "vehiculos_test")
                .withProperty("spring.flyway.default-schema", "vehiculos");

        assertThatThrownBy(() -> aplicar(entorno))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default-schema");
    }

    @Test
    void rechazaSinSchemaConfigurado() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("VEHICULOS_DB_URL", "jdbc:postgresql://localhost:1/ficticia")
                .withProperty("VEHICULOS_DB_USUARIO", "usuario_ficticio")
                .withProperty("VEHICULOS_DB_CLAVE", "clave_ficticia");
        entorno.setActiveProfiles("supabase");

        assertThatThrownBy(() -> aplicar(entorno)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nombraLasVariablesQueFaltanSinMostrarValores() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("VEHICULOS_DB_URL", "jdbc:postgresql://localhost:1/ficticia")
                .withProperty("VEHICULOS_DB_USUARIO", " ")
                .withProperty("spring.flyway.schemas", "vehiculos_test")
                .withProperty("spring.jpa.properties.hibernate.default_schema", "vehiculos_test");
        entorno.setActiveProfiles("supabase");

        assertThatThrownBy(() -> aplicar(entorno))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VEHICULOS_DB_USUARIO")
                .hasMessageContaining("VEHICULOS_DB_CLAVE")
                .hasMessageNotContaining("VEHICULOS_DB_URL")
                .hasMessageNotContaining("localhost");
    }

    @Test
    void noIntervieneEnTestcontainers() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("spring.flyway.schemas", "vehiculos")
                .withProperty("spring.jpa.properties.hibernate.default_schema", "vehiculos");
        entorno.setActiveProfiles("test");

        assertThatCode(() -> aplicar(entorno)).doesNotThrowAnyException();
    }

    @Test
    void conUnaUrlConfiguradaIntervieneAunqueFalteElPerfil() {
        MockEnvironment entorno = supabase("vehiculos", "vehiculos")
                .withProperty("spring.datasource.url", "${VEHICULOS_DB_URL}");
        entorno.setActiveProfiles("test", "local");

        assertThatThrownBy(() -> aplicar(entorno))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vehiculos_test");
    }

    @Test
    void elArranqueContraUnSchemaDistintoSeNiegaAntesDeConectarse() {
        SpringApplicationBuilder aplicacion = new SpringApplicationBuilder(VehiculosApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test", "supabase");

        assertThatThrownBy(() -> aplicacion.run(
                "--VEHICULOS_DB_URL=jdbc:postgresql://localhost:1/ficticia",
                "--VEHICULOS_DB_USUARIO=usuario_ficticio",
                "--VEHICULOS_DB_CLAVE=clave_ficticia",
                "--spring.datasource.url=jdbc:postgresql://localhost:1/ficticia",
                "--spring.flyway.schemas=vehiculos",
                "--spring.jpa.properties.hibernate.default_schema=vehiculos"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vehiculos_test")
                .hasMessageContaining("[vehiculos]");
    }
}
