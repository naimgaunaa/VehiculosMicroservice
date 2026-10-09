-- Seed de desarrollo: 10 vehículos ficticios con su fila inicial en precio_historial.
-- Solo corre con el perfil `seed` (application-seed.yml suma classpath:db/seed a spring.flyway.locations).
--
-- Migración repetible: Flyway la ejecuta después de las versionadas y la vuelve a ejecutar si cambia este archivo.
-- Es idempotente (ids fijos + ON CONFLICT DO NOTHING): no duplica filas, pero tampoco actualiza las ya cargadas.
-- Para cambiar un dato ya sembrado hay que borrar la fila a mano (primero precio_historial, después vehiculo).
--
-- Los datos son ficticios. fotos: todas en NULL. creado_en = actualizado_en = vigente_desde de la fila inicial.

INSERT INTO vehiculo (id, marca, modelo, version, anio, kilometraje, precio, disponible, caja, foto_url, creado_en, actualizado_en)
VALUES
    ('a0000000-0000-4000-8000-000000000001', 'Toyota',     'Corolla', 'XEI 2.0 CVT',  2022,  28000, 24500000.00, TRUE,  'AUTOMATICA', NULL, '2026-09-01T10:00:00Z', '2026-09-01T10:00:00Z'),
    ('a0000000-0000-4000-8000-000000000002', 'Ford',       'Fiesta',  'SE 1.6',       2015,  96000,  9800000.00, TRUE,  'MANUAL',      NULL, '2026-09-02T10:00:00Z', '2026-09-02T10:00:00Z'),
    ('a0000000-0000-4000-8000-000000000003', 'Volkswagen', 'Amarok',  'V6 Highline',  2023,  15000, 52000000.00, TRUE,  'AUTOMATICA', NULL, '2026-09-03T10:00:00Z', '2026-09-03T10:00:00Z'),
    ('a0000000-0000-4000-8000-000000000004', 'Chevrolet',  'Onix',    NULL,           2019,  64000, 13200000.00, TRUE,  'MANUAL',      NULL, '2026-09-04T10:00:00Z', '2026-09-04T10:00:00Z'),
    ('a0000000-0000-4000-8000-000000000005', 'Renault',    'Sandero', 'Stepway',      2018,  81000, 11500000.00, FALSE, 'MANUAL',      NULL, '2026-09-05T10:00:00Z', '2026-09-05T10:00:00Z'),
    ('a0000000-0000-4000-8000-000000000006', 'Peugeot',    '208',     'Allure Pack',  2021,  39000, 17900000.00, TRUE,  'AUTOMATICA', NULL, '2026-09-06T10:00:00Z', '2026-09-06T10:00:00Z'),
    ('a0000000-0000-4000-8000-000000000007', 'Fiat',       'Cronos',  'Drive 1.3',    2020,  52000, 14800000.00, TRUE,  'MANUAL',      NULL, '2026-09-07T10:00:00Z', '2026-09-07T10:00:00Z'),
    ('a0000000-0000-4000-8000-000000000008', 'Honda',      'Civic',   NULL,           2009, 178000,  7200000.00, FALSE, 'MANUAL',      NULL, '2026-09-08T10:00:00Z', '2026-09-08T10:00:00Z'),
    ('a0000000-0000-4000-8000-000000000009', 'Nissan',     'Kicks',   'Exclusive CVT',2025,   8000, 31500000.00, TRUE,  'AUTOMATICA', NULL, '2026-09-09T10:00:00Z', '2026-09-09T10:00:00Z'),
    ('a0000000-0000-4000-8000-000000000010', 'Citroën',    'C3',      'Feel 1.6',     2017, 110000,  8900000.00, TRUE,  'MANUAL',      NULL, '2026-09-10T10:00:00Z', '2026-09-10T10:00:00Z')
ON CONFLICT (id) DO NOTHING;

-- Una fila por unidad, con el mismo precio que vehiculo.precio y vigente desde el alta.
INSERT INTO precio_historial (id, vehiculo_id, precio, vigente_desde)
VALUES
    ('b0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 24500000.00, '2026-09-01T10:00:00Z'),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000002',  9800000.00, '2026-09-02T10:00:00Z'),
    ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000003', 52000000.00, '2026-09-03T10:00:00Z'),
    ('b0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000004', 13200000.00, '2026-09-04T10:00:00Z'),
    ('b0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000005', 11500000.00, '2026-09-05T10:00:00Z'),
    ('b0000000-0000-4000-8000-000000000006', 'a0000000-0000-4000-8000-000000000006', 17900000.00, '2026-09-06T10:00:00Z'),
    ('b0000000-0000-4000-8000-000000000007', 'a0000000-0000-4000-8000-000000000007', 14800000.00, '2026-09-07T10:00:00Z'),
    ('b0000000-0000-4000-8000-000000000008', 'a0000000-0000-4000-8000-000000000008',  7200000.00, '2026-09-08T10:00:00Z'),
    ('b0000000-0000-4000-8000-000000000009', 'a0000000-0000-4000-8000-000000000009', 31500000.00, '2026-09-09T10:00:00Z'),
    ('b0000000-0000-4000-8000-000000000010', 'a0000000-0000-4000-8000-000000000010',  8900000.00, '2026-09-10T10:00:00Z')
ON CONFLICT (id) DO NOTHING;
