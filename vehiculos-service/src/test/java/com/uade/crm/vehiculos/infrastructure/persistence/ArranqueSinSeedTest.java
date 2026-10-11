package com.uade.crm.vehiculos.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.uade.crm.vehiculos.PruebaIntegracionSeed;

/**
 * Una base que ya recibió el seed tiene la repetible en {@code flyway_schema_history}. Al arrancar después sin el
 * perfil {@code seed} (por ejemplo solo con {@code local}), Flyway valida sin {@code db/seed} en sus locations.
 * Comparte contexto con {@link SeedTest} (mismas anotaciones), así que la base de este test ya está sembrada.
 * Copia la configuración de Flyway que armó Spring y solo cambia las locations.
 */
@PruebaIntegracionSeed
class ArranqueSinSeedTest {

    @Autowired
    private Flyway flyway;

    @Test
    void sinElPerfilSeedValidaAunqueElSeedYaEsteAplicado() {
        Flyway sinSeed = conLocations("classpath:db/migration");

        assertThatCode(sinSeed::validate).doesNotThrowAnyException();
    }

    /**
     * Lo que se ignora es solo la repetible faltante. {@code db/sin-v1} (recurso de test) tiene una V2 y no la V1,
     * así que la V1 aplicada queda "missing": tiene que seguir fallando.
     */
    @Test
    void unaVersionadaAplicadaQueFaltaEnElCodigoSigueFallando() {
        Flyway sinV1 = conLocations("classpath:db/sin-v1", "classpath:db/seed");

        assertThatThrownBy(sinV1::validate)
                .isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("Detected applied migration not resolved locally: 1");
    }

    private Flyway conLocations(String... locations) {
        return Flyway.configure().configuration(flyway.getConfiguration()).locations(locations).load();
    }
}
