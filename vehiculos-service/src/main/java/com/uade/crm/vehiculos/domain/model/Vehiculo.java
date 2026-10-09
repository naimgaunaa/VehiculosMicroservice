package com.uade.crm.vehiculos.domain.model;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Unidad del inventario. Se crea con {@link #builder(Clock)}, que valida las invariantes en {@code build()};
 * los mutadores validan las mismas reglas al actualizar.
 */
public final class Vehiculo {

    static final int ANIO_MINIMO = 1950;
    /** Escala y dígitos enteros de {@code precio NUMERIC(14, 2)}. */
    static final int DECIMALES_PRECIO = 2;
    static final int ENTEROS_PRECIO = 12;

    private final UUID id;
    private final String marca;
    private final String modelo;
    private final String version;
    private final int anio;
    private final int kilometraje;
    private BigDecimal precio;
    private boolean disponible;
    private final Caja caja;
    private String fotoUrl;

    private Vehiculo(Builder builder, int anio, int kilometraje, BigDecimal precio) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.marca = builder.marca.strip();
        this.modelo = builder.modelo.strip();
        this.version = builder.version;
        this.anio = anio;
        this.kilometraje = kilometraje;
        this.precio = precio;
        this.disponible = builder.disponible;
        this.caja = builder.caja;
        this.fotoUrl = builder.fotoUrl;
    }

    /** El {@code clock} fija el año actual contra el que se valida {@code anio}. */
    public static Builder builder(Clock clock) {
        return new Builder(Objects.requireNonNull(clock, "El clock es obligatorio"));
    }

    public UUID id() {
        return id;
    }

    public String marca() {
        return marca;
    }

    public String modelo() {
        return modelo;
    }

    public Optional<String> version() {
        return Optional.ofNullable(version);
    }

    public int anio() {
        return anio;
    }

    public int kilometraje() {
        return kilometraje;
    }

    public BigDecimal precio() {
        return precio;
    }

    public boolean disponible() {
        return disponible;
    }

    public Caja caja() {
        return caja;
    }

    public Optional<String> fotoUrl() {
        return Optional.ofNullable(fotoUrl);
    }

    public void actualizarPrecio(BigDecimal nuevoPrecio) {
        this.precio = validarPrecio(nuevoPrecio);
    }

    public void cambiarDisponibilidad(boolean disponible) {
        this.disponible = disponible;
    }

    public void asignarFoto(String url) {
        if (url == null || url.isBlank()) {
            throw new VehiculoInvalidoException("La URL de la foto no puede estar vacía");
        }
        this.fotoUrl = url;
    }

    /**
     * Valida contra la columna {@code NUMERIC(14, 2)} y normaliza a 2 decimales, para que el precio en memoria
     * sea el mismo que devuelve la base ({@code 10000000} queda {@code 10000000.00}).
     */
    private static BigDecimal validarPrecio(BigDecimal precio) {
        if (precio == null || precio.signum() <= 0) {
            throw new VehiculoInvalidoException("El precio debe ser mayor que cero");
        }
        if (precio.stripTrailingZeros().scale() > DECIMALES_PRECIO) {
            throw new VehiculoInvalidoException("El precio admite como máximo " + DECIMALES_PRECIO + " decimales");
        }
        if (precio.precision() - precio.scale() > ENTEROS_PRECIO) {
            throw new VehiculoInvalidoException(
                    "El precio admite como máximo " + ENTEROS_PRECIO + " dígitos enteros");
        }
        return precio.setScale(DECIMALES_PRECIO);
    }

    private static void textoObligatorio(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new VehiculoInvalidoException(mensaje);
        }
    }

    public static final class Builder {

        private final Clock clock;
        private UUID id;
        private String marca;
        private String modelo;
        private String version;
        private Integer anio;
        private Integer kilometraje;
        private BigDecimal precio;
        private boolean disponible = true;
        private Caja caja;
        private String fotoUrl;

        private Builder(Clock clock) {
            this.clock = clock;
        }

        /** Opcional: si no se informa, {@code build()} genera uno. La persistencia lo informa al reconstruir. */
        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder marca(String marca) {
            this.marca = marca;
            return this;
        }

        public Builder modelo(String modelo) {
            this.modelo = modelo;
            return this;
        }

        /** Opcional. Un texto en blanco equivale a no informarla. */
        public Builder version(String version) {
            this.version = version == null || version.isBlank() ? null : version.strip();
            return this;
        }

        public Builder anio(int anio) {
            this.anio = anio;
            return this;
        }

        public Builder kilometraje(int kilometraje) {
            this.kilometraje = kilometraje;
            return this;
        }

        public Builder precio(BigDecimal precio) {
            this.precio = precio;
            return this;
        }

        /** Por defecto {@code true}. */
        public Builder disponible(boolean disponible) {
            this.disponible = disponible;
            return this;
        }

        public Builder caja(Caja caja) {
            this.caja = caja;
            return this;
        }

        public Builder fotoUrl(String fotoUrl) {
            this.fotoUrl = fotoUrl;
            return this;
        }

        public Vehiculo build() {
            textoObligatorio(marca, "La marca no puede estar vacía");
            textoObligatorio(modelo, "El modelo no puede estar vacío");
            int anioValido = validarAnio();
            int kilometrajeValido = validarKilometraje();
            BigDecimal precioValido = validarPrecio(precio);
            if (caja == null) {
                throw new VehiculoInvalidoException("La caja es obligatoria");
            }
            return new Vehiculo(this, anioValido, kilometrajeValido, precioValido);
        }

        private int validarAnio() {
            int maximo = Year.now(clock).getValue() + 1;
            if (anio == null || anio < ANIO_MINIMO || anio > maximo) {
                throw new VehiculoInvalidoException(
                        "El año debe estar entre " + ANIO_MINIMO + " y " + maximo);
            }
            return anio;
        }

        private int validarKilometraje() {
            if (kilometraje == null || kilometraje < 0) {
                throw new VehiculoInvalidoException("El kilometraje debe ser mayor o igual a 0");
            }
            return kilometraje;
        }
    }
}
