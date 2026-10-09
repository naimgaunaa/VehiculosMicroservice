package com.uade.crm.vehiculos.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración propia del servicio (variables {@code VEHICULOS_*}, mapeadas en {@code application.yml}).
 * Si falta una obligatoria o quedó un placeholder sin completar, el servicio no arranca. Las variables
 * de la base ({@code VEHICULOS_DB_*}) las resuelve {@code spring.datasource} y fallan igual si faltan.
 *
 * @param modoFotos            estrategia de {@code FotoStorage}
 * @param bucketFotos          bucket público de Supabase Storage
 * @param supabaseUrl          obligatoria solo en modo {@code supabase}
 * @param supabaseClaveSecreta obligatoria solo en modo {@code supabase}; nunca se loguea
 * @param claveEscritura       secreto del header {@code X-Api-Key}; mínimo 32 caracteres; nunca se loguea
 */
@Validated
@ConfigurationProperties(prefix = "vehiculos")
public record VehiculosProperties(
        @NotNull(message = "VEHICULOS_FOTOS_MODO es obligatoria (supabase o deshabilitado)")
        ModoFotos modoFotos,
        @NotBlank(message = "VEHICULOS_FOTOS_BUCKET es obligatoria")
        @Pattern(regexp = SIN_PLACEHOLDER, message = "VEHICULOS_FOTOS_BUCKET tiene un placeholder sin completar")
        String bucketFotos,
        @Pattern(regexp = SIN_PLACEHOLDER, message = "VEHICULOS_SUPABASE_URL tiene un placeholder sin completar")
        String supabaseUrl,
        @Pattern(regexp = SIN_PLACEHOLDER,
                message = "VEHICULOS_SUPABASE_CLAVE_SECRETA tiene un placeholder sin completar")
        String supabaseClaveSecreta,
        @NotBlank(message = "VEHICULOS_CLAVE_ESCRITURA es obligatoria")
        @Size(min = 32, message = "VEHICULOS_CLAVE_ESCRITURA debe tener al menos 32 caracteres")
        @Pattern(regexp = SIN_PLACEHOLDER,
                message = "VEHICULOS_CLAVE_ESCRITURA tiene un placeholder sin completar")
        String claveEscritura) {

    /** Rechaza valores con {@code <...>} (copiados de {@code .env.example}) o {@code ${...}} sin resolver. */
    static final String SIN_PLACEHOLDER = "^(?!.*(<.*>|\\$\\{)).*$";

    public enum ModoFotos {
        SUPABASE, DESHABILITADO
    }

    @AssertTrue(message = "VEHICULOS_SUPABASE_URL es obligatoria en modo supabase")
    boolean isSupabaseUrlCompleta() {
        return modoFotos != ModoFotos.SUPABASE || tieneTexto(supabaseUrl);
    }

    @AssertTrue(message = "VEHICULOS_SUPABASE_CLAVE_SECRETA es obligatoria en modo supabase")
    boolean isSupabaseClaveCompleta() {
        return modoFotos != ModoFotos.SUPABASE || tieneTexto(supabaseClaveSecreta);
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    @Override
    public String toString() {
        return "VehiculosProperties[modoFotos=" + modoFotos
                + ", bucketFotos=" + bucketFotos
                + ", supabaseUrl=" + supabaseUrl
                + ", supabaseClaveSecreta=****"
                + ", claveEscritura=****]";
    }
}
