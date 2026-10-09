package com.uade.crm.vehiculos.api.error;

import java.net.URI;

import org.springframework.http.HttpStatus;

/**
 * Códigos de error estables de la API. Cada uno fija el status HTTP, el {@code title} y el slug
 * con el que se arma el {@code type} del {@code ProblemDetail}.
 */
public enum CodigoError {

    VALIDACION(HttpStatus.UNPROCESSABLE_ENTITY, "validacion", "Datos inválidos"),
    JSON_INVALIDO(HttpStatus.BAD_REQUEST, "json-invalido", "JSON mal formado"),
    SOLICITUD_INVALIDA(HttpStatus.BAD_REQUEST, "solicitud-invalida", "Solicitud inválida"),
    CLAVE_SERVICIO_INVALIDA(HttpStatus.UNAUTHORIZED, "clave-servicio-invalida", "Clave de servicio inválida"),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "error-interno", "Error interno"),
    VEHICULO_NO_ENCONTRADO(HttpStatus.NOT_FOUND, "vehiculo-no-encontrado", "Vehículo no encontrado"),
    FOTO_INVALIDA(HttpStatus.UNPROCESSABLE_ENTITY, "foto-invalida", "Foto inválida"),
    ALMACENAMIENTO_NO_DISPONIBLE(HttpStatus.SERVICE_UNAVAILABLE, "almacenamiento-no-disponible",
            "Almacenamiento no disponible");

    private static final String BASE_TYPE = "https://crm.uade.local/errores/";

    private final HttpStatus status;
    private final String slug;
    private final String titulo;

    CodigoError(HttpStatus status, String slug, String titulo) {
        this.status = status;
        this.slug = slug;
        this.titulo = titulo;
    }

    public HttpStatus status() {
        return status;
    }

    public String titulo() {
        return titulo;
    }

    public URI type() {
        return URI.create(BASE_TYPE + slug);
    }
}
