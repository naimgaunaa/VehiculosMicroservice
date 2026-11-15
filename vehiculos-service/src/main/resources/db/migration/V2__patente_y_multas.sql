-- Patente de cada unidad y resultado de la última consulta de multas (flujo CONSULTA_MULTAS_VEHICULO del
-- orquestador: el servicio `multas` consulta la ANSV por patente y el orquestador entrega el resultado acá).
-- Corre en el schema `vehiculos` (spring.flyway.schemas). Las unidades que ya existen quedan sin patente.

ALTER TABLE vehiculo
    ADD COLUMN patente               VARCHAR(7),
    ADD COLUMN multas_consultadas_en TIMESTAMPTZ,
    ADD COLUMN multas_subtotal       NUMERIC(14, 2) CHECK (multas_subtotal >= 0);

-- Dos unidades no pueden tener la misma patente; varias pueden no tenerla todavía.
CREATE UNIQUE INDEX ux_vehiculo_patente ON vehiculo (patente) WHERE patente IS NOT NULL;

-- Actas de la última consulta. Cada resultado nuevo reemplaza a las anteriores de esa unidad. Son datos
-- personales asociados a una patente: se exponen solo con la clave de servicio.
CREATE TABLE multa (
    id                    UUID PRIMARY KEY,
    vehiculo_id           UUID           NOT NULL REFERENCES vehiculo (id),
    numero_acta           VARCHAR(60)    NOT NULL,
    importe               NUMERIC(14, 2) NOT NULL CHECK (importe >= 0),
    importe_sin_descuento NUMERIC(14, 2) CHECK (importe_sin_descuento >= 0),
    tipo_infraccion       VARCHAR(500),
    lugar                 VARCHAR(200),
    juzgado               VARCHAR(200),
    CONSTRAINT uq_multa_vehiculo_acta UNIQUE (vehiculo_id, numero_acta)
);

CREATE INDEX ix_multa_vehiculo ON multa (vehiculo_id);
