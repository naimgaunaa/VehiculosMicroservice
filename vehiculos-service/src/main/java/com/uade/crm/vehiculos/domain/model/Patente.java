package com.uade.crm.vehiculos.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/** Patente argentina: formato anterior ({@code AAA123}) o Mercosur ({@code AB123CD}). */
public final class Patente {

    /** Largo máximo de la columna {@code vehiculo.patente}. */
    public static final int LARGO_MAXIMO = 7;

    private static final Pattern FORMATO = Pattern.compile("^([A-Z]{3}[0-9]{3}|[A-Z]{2}[0-9]{3}[A-Z]{2})$");

    private Patente() {
    }

    /** Sin espacios ni guiones y en mayúsculas ({@code "ab 123-cd"} queda {@code "AB123CD"}). */
    public static String normalizar(String patente) {
        if (patente == null || patente.isBlank()) {
            throw new VehiculoInvalidoException("La patente no puede estar vacía");
        }
        String normalizada = patente.strip().toUpperCase(Locale.ROOT).replaceAll("[\s-]", "");
        if (!FORMATO.matcher(normalizada).matches()) {
            throw new VehiculoInvalidoException("La patente debe tener el formato AAA123 o AB123CD");
        }
        return normalizada;
    }
}
