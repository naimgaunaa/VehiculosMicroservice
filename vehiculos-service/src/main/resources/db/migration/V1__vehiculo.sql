-- Inventario de vehículos y su historial de precios. Corre en el schema `vehiculos` (spring.flyway.schemas).
-- La tabla flyway_schema_history ya la crea Flyway: no se declara acá.

CREATE TABLE vehiculo (
    id             UUID PRIMARY KEY,
    marca          VARCHAR(60)    NOT NULL,
    modelo         VARCHAR(80)    NOT NULL,
    version        VARCHAR(80),
    anio           INTEGER        NOT NULL,
    kilometraje    INTEGER        NOT NULL CHECK (kilometraje >= 0),
    precio         NUMERIC(14, 2) NOT NULL CHECK (precio > 0),
    disponible     BOOLEAN        NOT NULL DEFAULT TRUE,
    caja           VARCHAR(15)    NOT NULL CHECK (caja IN ('AUTOMATICA', 'MANUAL')),
    foto_url       VARCHAR(500),
    creado_en      TIMESTAMPTZ    NOT NULL,
    actualizado_en TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_vehiculo_disponible ON vehiculo (disponible);

-- Solo de agregado: una fila por cada precio que rigió, desde vigente_desde.
CREATE TABLE precio_historial (
    id            UUID PRIMARY KEY,
    vehiculo_id   UUID           NOT NULL REFERENCES vehiculo (id),
    precio        NUMERIC(14, 2) NOT NULL CHECK (precio > 0),
    vigente_desde TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_precio_historial_vehiculo ON precio_historial (vehiculo_id, vigente_desde DESC);
