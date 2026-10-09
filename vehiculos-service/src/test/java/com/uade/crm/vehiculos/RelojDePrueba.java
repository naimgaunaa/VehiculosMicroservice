package com.uade.crm.vehiculos;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj que arranca en un instante fijo y solo avanza cuando el test lo pide. */
public final class RelojDePrueba extends Clock {

    /** Año actual 2026, por lo que el máximo aceptado por el Builder es 2027. */
    public static final Instant INICIO = Instant.parse("2026-10-09T12:00:00Z");

    private Instant ahora = INICIO;

    public void avanzar(Duration duracion) {
        ahora = ahora.plus(duracion);
    }

    /** El reloj es un bean del contexto compartido: cada test lo devuelve a {@link #INICIO}. */
    public void reiniciar() {
        ahora = INICIO;
    }

    @Override
    public Instant instant() {
        return ahora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        throw new UnsupportedOperationException("El reloj de prueba solo trabaja en UTC");
    }
}
