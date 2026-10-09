package com.uade.crm.vehiculos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

class DominioConfigTest {

    private final Clock reloj = new DominioConfig().reloj();

    @Test
    void elRelojEstaEnUtc() {
        assertThat(reloj.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    /** Muchas muestras: con {@code systemUTC()} solo, alguna traería nanosegundos sueltos. */
    @Test
    void losInstantesNoTienenMasPrecisionQueTimestamptz() {
        for (int i = 0; i < 10_000; i++) {
            Instant ahora = reloj.instant();

            assertThat(ahora).isEqualTo(ahora.truncatedTo(ChronoUnit.MICROS));
        }
    }
}
