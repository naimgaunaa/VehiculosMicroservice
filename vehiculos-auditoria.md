# Auditoría final de `vehiculos`

Fecha: 2026-10-11. Rama: `feature/vehiculos-auditoria`, creada desde `dev` (`ee7e93a`). Fuera de este informe no se modificó ningún archivo del repo.

## Cómo se hizo

- Se leyeron `CLAUDE.md` y `docs/arquitectura.md` completos y todo `src/main`, `src/test`, `Dockerfile`, `.dockerignore`, `.gitignore`, `infra/` y `scripts/`.
- `mvn -pl vehiculos-service -am verify` (Testcontainers): **350 tests, 0 fallas, 0 errores**. Arrancan 6 contextos (2 con base y 4 `@WebMvcTest`), como dice §12. No se corrió `-Psupabase`.
- Las reproducciones se hicieron en una copia del repo en el scratchpad, sin `.git` ni `infra/.env`. Sus tests no están en el repo.
- Base real: solo `SELECT`, con el rol `vehiculos_app`, y la segunda tanda dentro de `BEGIN READ ONLY`. Ojo: `PGOPTIONS=default_transaction_read_only` **no** pasa por el session pooler, así que esa protección no sirve con Supabase. Storage: solo `POST /object/list` (lectura). No se escribió nada.
- Secretos: `git log --all -p` de los 37 commits, más `git grep`, comparados contra los valores reales de las `VEHICULOS_*` cargadas, sin imprimirlos.

---

## Hallazgos por gravedad

### ALTA

**A1. Dos `PUT` concurrentes dejan el historial incoherente con `vehiculo.precio` (lost update).**
- Dónde: `ActualizacionVehiculoService.java:29` lee sin bloquear y `:43` guarda la unidad entera. `VehiculoRepositorioJpa.java:64-67` copia todos los campos del dominio sobre la entidad, y Hibernate hace `UPDATE` de todas las columnas. Lo mismo pasa en la transacción corta de `FotoVehiculoService.java:45-49`.
- Escenario: T2 (`PUT {"disponible": false}`) lee la unidad con precio 1.000.000. T1 (`PUT {"precio": 2000000}`) cambia el precio, agrega la fila al historial y confirma. Después T2 guarda y vuelve a escribir `precio = 1.000.000`.
- Evidencia: test de reproducción `CarreraDePutsTest` en la copia, contra PostgreSQL de Testcontainers. Salida: `precio_vehiculo=1000000.00 ultima_fila_historial=2000000.00`. Se rompe la regla de §6: "su fila más reciente coincide siempre con `vehiculo.precio`".
- El documento acepta que "gana el último `PUT`", pero acá el último `PUT` ni siquiera tocó el precio y lo revirtió igual. La nota de §13 (etapa 6, "Concurrencia") solo contempla el 500 de dos cambios de precio en el mismo microsegundo, y ese caso sí queda coherente.
- Qué cambiaría (**corregir el código**, la regla del doc es la correcta):
  - Agregar al puerto `buscarPorIdParaActualizar(UUID)`, implementado con `@Lock(LockModeType.PESSIMISTIC_WRITE)` en `VehiculoJpaRepository` (`SELECT … FOR UPDATE`).
  - Usarlo en `ActualizacionVehiculoService` y en `asignarYGuardar` de `FotoVehiculoService`.
  - `clock.instant()` ya se toma después de la lectura, así que con el bloqueo también desaparece el 500 por instantes invertidos.
  - La semántica sigue siendo "gana el último" y no hay `@Version`.
  - Subir `CarreraDePutsTest` al repo y verlo fallar antes del arreglo.
  - Actualizar §6 ("Sin control de concurrencia optimista: …, con bloqueo de la fila en las escrituras") y la nota de concurrencia de §13.

### MEDIA

**M1. ~~La base compartida y el bucket tienen restos de pruebas que invalidan los números del seed (10/8/2).~~ Descartado:** el responsable confirmó el 2026-10-11 que esos datos están a propósito. No hay que limpiarlos. Queda solo el registro de lo que se encontró en la consulta de solo lectura:
- `vehiculos.vehiculo`: hay 11 unidades (9 disponibles). Sobra `bdeba8e2-1efe-4d14-8d4b-f0e539404648` (Honda City, 1 fila de historial, creada el 2026-10-11 00:27).
- El Corolla (`a0000000-…01`) quedó con precio 16.700.000 y una segunda fila de historial del 2026-10-11 00:30. El seed lo deja en 24.500.000.
- El Fiesta (`a0000000-…02`) tiene `actualizado_en` 2026-10-10 21:10, y el seed dice 2026-09-02 10:00.
- En el bucket `vehiculos-fotos` hay un objeto huérfano: `8ab1e0d0-c63c-4e68-8ea4-257dbdfeb9a7.jpg` (86 KB, 2026-10-11 01:09), que no corresponde a ninguna unidad.
- Está bien: Flyway tiene la `V1` y `seed vehiculos` con `success = t`. El historial coincide con el precio en las 11 unidades y no hay instantes repetidos. Ya se limpiaron las unidades "Ficticia" de las etapas 6 y 8, la foto del Fiesta y el `actualizado_en` del Sandero.
- Qué cambiaría: avisar al equipo (regla 4) y correr:
  ```sql
  DELETE FROM vehiculos.precio_historial WHERE vehiculo_id = 'bdeba8e2-1efe-4d14-8d4b-f0e539404648';
  DELETE FROM vehiculos.vehiculo WHERE id = 'bdeba8e2-1efe-4d14-8d4b-f0e539404648';
  DELETE FROM vehiculos.precio_historial WHERE vehiculo_id = 'a0000000-0000-4000-8000-000000000001' AND id::text NOT LIKE 'b0000000-%';
  UPDATE vehiculos.vehiculo SET precio = 24500000.00, actualizado_en = '2026-09-01 10:00:00+00' WHERE id = 'a0000000-0000-4000-8000-000000000001';
  UPDATE vehiculos.vehiculo SET actualizado_en = '2026-09-02 10:00:00+00' WHERE id = 'a0000000-0000-4000-8000-000000000002';
  ```
  Y borrar `8ab1e0d0-….jpg` desde el dashboard. Todo eso hay que repetirlo después de cada ensayo de la demo.

**M2. La consigna E7 del paso a paso falla con la colección de Postman.** `docs/paso-a-paso-claude-code.md:311` espera `415`, pero con el header `Content-Type: multipart/form-data` que trae la colección la respuesta correcta es `400 SOLICITUD_INVALIDA` (pendiente de la etapa 9, sin corregir). Qué cambiaría (**corregir el doc**): E7 con el header destildado da 415, y un caso E7b con el header puesto da 400.

**M3. §7 contradice la decisión de la etapa 9 sobre el orden de errores.** `docs/arquitectura.md:295` dice "401 → 404 → 422 → 503", y las decisiones de la etapa 9 (línea 814) dicen "401 → 415/400 → 404 → 422 → 503". El código sigue el segundo, cubierto por `FotoApiTest`. **Corregir el doc** (§7 y la fila de `PUT /vehiculos/{id}/foto` en §5.1, que no lista el 400 por multipart mal formado).

**M4. `CLAUDE.md` no está versionado y el `README.md` está vacío.** `.gitignore:8` ignora `CLAUDE.md`, que nunca se commiteó (`git log --all -- CLAUDE.md` no devuelve nada), y `README.md` tiene una sola línea. Sin embargo, `arquitectura.md` cita "la regla 4 de `CLAUDE.md`" y otras más en varios lugares. Quien clone el repo (equipo o docentes) no tiene las reglas ni cómo correrlo. Qué cambiaría (**decisión tuya**): versionar `CLAUDE.md`, o pasar las reglas y los comandos al README.

### BAJA

**B1. `@Max(2027)` fijo en el DTO, mientras el dominio usa "año actual + 1".** `AltaVehiculoRequest.java:21`. Desde el 1/1/2027 el DTO rechaza modelos 2028 que el dominio acepta, y en 2028 rechaza modelos del año en curso. Ya está anotado en §13 (etapa 6) y sigue vigente. Para el TP (2026) **puede quedar como está y documentado**. Si el servicio sigue vivo, conviene un validador `@AnioDeModelo` que use el `Clock`.

**B2. El comentario de `application.yml:38` está desactualizado** (pendiente de la etapa 8, vigente). Dice que Spring falla con `Could not resolve placeholder`, pero el binding recibe el texto literal `${VAR}` y lo rechaza `VehiculosProperties` (`SIN_PLACEHOLDER`, `@Size`) o la conversión del enum. Texto propuesto: `# Sin valor por defecto: si falta una variable, el binding recibe el texto literal ${VAR} y VehiculosProperties lo rechaza.`

**B3. Jackson acepta decimales y textos en campos enteros.** Probado en la copia: `"anio": 2021.9` y `"kilometraje": 45000.7` llegan como 2021 y 45000 sin error, y `"anio": "2021"` o `"disponible": "false"` también se aceptan. Qué cambiaría: `spring.jackson.deserialization.accept-float-as-int: false` en `application.yml`. Con eso el handler ya responde 422 "el valor no tiene el tipo esperado".

**B4. `.gitignore` no cubre variantes de `.env`.** `.gitignore:1` ignora solo archivos llamados exactamente `.env`, así que un `infra/.env.local` o `.env.supabase` se podría commitear. `.dockerignore` sí las cubre. Qué cambiaría: agregar `.env.*` y `!.env.example`.

**B5. Ciclo de paquetes entre `api` y `config`.**
- `WebConfig.java:8` (config) importa `api.seguridad.ClaveServicioInterceptor`.
- `ApiExceptionHandler.java:45` (api) importa `config.CorrelationIdFilter`.
- `ClaveServicioInterceptor.java:11` importa `config.VehiculosProperties`.

No rompe la regla del dominio (el dominio no importa nada externo, verificado con grep) y §2 ubica `WebConfig` en `config`. Es una mejora, no un defecto.

**B6. La colección de Postman manda una clave literal por request.** En `docs/Servicio de Vehículos.postman_collection.json` cada request trae el header `X-Api-Key: <API Key>`, además del auth de colección `{{apiKey}}`. No es un secreto, pero el header literal puede tapar al auth. No está verificado en Postman; revisarlo al reimportar.

**B7. El test `VehiculoEscrituraApiTest` depende de que otras clases reinicien el reloj.** `VehiculoEscrituraApiTest.java:53` reinicia `RelojDePrueba` solo en `@AfterEach`. Hoy todas las clases que lo avanzan lo reinician, pero un test nuevo que se olvide lo rompería según el orden. Qué cambiaría: reiniciarlo también en `@BeforeEach`.

**B8. Huecos en `docs/arquitectura.md`, lo que el código hace y el doc no dice (corregir el doc):**
- §2 no lista `AltaVehiculo`, `DatosInvalidosException` (`application`), `Precios` (`api.dto`) ni `VehiculosProperties.ModoFotos`.
- §3.2 no dice que `CorrelationIdFilter` descarta un `X-Correlation-Id` que no cumpla `[A-Za-z0-9._-]{1,64}` y genera uno nuevo (`CorrelationIdFilter.java:29`).
- §9.3 y §10 no mencionan `spring.flyway.ignore-migration-patterns: "*:future,repeatable:missing"` (`application.yml:36`), del que depende arrancar sin `seed` sobre una base sembrada (`ArranqueSinSeedTest`).
- §5.3 muestra `"precio": 10000000`, pero la API siempre responde con escala 2 (`10000000.00`, `Precios.aEscala2`). Swagger ya lo muestra bien.
- §12 habla de 346 tests y hoy son 350.

**B9. El compose publica el puerto 8083 en todas las interfaces** (`infra/docker-compose.yml:9`). Las lecturas quedan abiertas a la red local. Si no se necesita, `127.0.0.1:8083:8083`. Es una mejora.

### Lo que el documento pide y el código no hace

Solo A1: el invariante del historial no se sostiene bajo concurrencia. El resto de §3 a §7 y §9 está implementado y cubierto por tests:
- endpoints y status;
- los 8 códigos con `type`, `title`, `codigo`, `correlationId` y `errores`;
- `Location` en el 201;
- 401 antes de leer el cuerpo (también en multipart, con `resolve-lazily`);
- invariantes del Builder;
- normalización del precio;
- historial en la misma transacción;
- `actualizado_en` solo si hay cambios;
- detección de la foto por firma, límite de 2 MiB inclusive y 3 MB de Spring, ambos en 422;
- borrado del objeto viejo después de confirmar, con WARN si falla;
- timeouts de 5 y 15 s, `toBodilessEntity`, `apikey` sin Bearer;
- `V1` idéntica a §9.2;
- schema `vehiculos` en Hibernate y Flyway.

---

## Seguridad

| Chequeo | Resultado | Evidencia |
|---|---|---|
| Secretos en el historial de git | OK | En 37 commits, ningún valor real de `VEHICULOS_DB_URL/USUARIO/CLAVE`, `SUPABASE_URL/CLAVE_SECRETA` ni `CLAVE_ESCRITURA`. Tampoco el project-ref. Los `sb_secret_…` y `--VEHICULOS_DB_CLAVE=` del historial son fixtures `ficticia` de tests |
| Secretos en el árbol | OK | Lo mismo con `git grep`. El único dato real es el host del pooler (`aws-1-sa-east-1…`) en `arquitectura.md:791`, que no es secreto |
| `.gitignore` / `.dockerignore` | OK, con B4 | `.dockerignore` es lista blanca con `**/.env*` al final. `.env.example` solo tiene placeholders |
| `toString` | OK | `VehiculosProperties.toString` enmascara las dos claves. Las entidades solo muestran el `id`. `SupabaseFotoStorage` no guarda la clave en un campo |
| Logs | OK | Storage loguea operación, objeto, status y cuerpo, nunca headers. `SecretosEnLogsTest` (TRACE) y `ClaveServicioTest` lo cubren |
| Respuestas de error | OK | 401 sin el valor recibido ni el configurado. 500 sin detalle |
| Archivo subido | OK | Firma de bytes, vacío, 2 MiB. Nombre y `Content-Type` del cliente ignorados. El nombre del objeto sale del UUID y del tipo detectado, sin path traversal |
| `X-Api-Key` en tiempo constante | OK con salvedad | `MessageDigest.isEqual` (`ClaveServicioInterceptor.java:33`) revela el largo. Ya está en `mejoras.md` (comparar SHA-256) |
| `creadoEn` / `actualizadoEn` en lecturas | OK | `VehiculoResponse` no los tiene. Hay tests en `VehiculoControllerTest:125`, `VehiculoApiSeedTest:53,88,161` (OpenAPI) |
| Base | OK | El rol es `vehiculos_app`, `public` sin `CREATE`. Ni `anon` ni `authenticated` tienen grants sobre `vehiculos.*`, así que RLS apagado no expone nada |

## Transacciones y consistencia

- **Historial atómico:** OK en un solo hilo. El alta y el `PUT` hacen `guardar` y `registrarPrecio` en el mismo `@Transactional`, y el adaptador exige `MANDATORY`. `AtomicidadActualizacionTest` prueba el rollback del `PUT`. Falta el test equivalente para el alta, aunque es atómica por construcción. Bajo concurrencia falla: ver A1.
- **`actualizado_en`:** OK. Lo fija el adaptador y solo se llama a `guardar` si algo cambió (`VehiculoEscrituraApiTest` 117 y 130). La foto también lo renueva.
- **Falla de Storage a mitad de la subida:**
  - Si falla la subida: 503 y no se guarda nada (test `siFallaElAlmacenamientoDa503SinGuardar`).
  - Si la subida sale bien y falla la base: la base sigue coherente y queda un objeto huérfano, como acepta §7. Si cambió la extensión, el objeto viejo sigue y el nuevo queda huérfano. No hay test de este caso.
  - Si falla el borrado del objeto viejo: WARN y 200 (testeado).
- **Dos `PUT` concurrentes:** dos cambios de precio quedan coherentes (gana el que confirma último, o el segundo da 500 si su instante es anterior). **Un cambio de precio contra uno de disponibilidad (o contra la foto) deja el historial incoherente:** reproducido en A1.

## Arquitectura y convenciones

- El dominio no importa Spring, JPA ni HTTP (grep vacío). `application` solo usa puertos.
- Las entidades, los repositorios Spring Data, el adaptador, `FotoStorageConfig`, `SupabaseFotoStorage` y `FotoStorageDeshabilitado` son de visibilidad de paquete.
- Los 13 paquetes de `main` tienen `package-info.java`.
- Los DTOs son `record`: las respuestas tienen `de(...)` y el alta recorta en el constructor compacto. No hay Lombok ni MapStruct.
- Patrones:
  - Repository: `VehiculoRepositorio` y `VehiculoRepositorioJpa`.
  - Builder: `Vehiculo.Builder`, con invariantes en `build()` y usado también para reconstruir desde la base.
  - Strategy: `FotoStorage` con `switch` en `FotoStorageConfig`.

  Los tres son visibles y están bien aplicados.
- Desvíos menores: B5 (ciclo `api`↔`config`) y B8 (doc).

## Tests

- **Ninguno llama a Supabase Storage.**
  - Los de integración usan `FotoStorageEnMemoria` `@Primary` (`PruebaIntegracion.java:24`) y modo `deshabilitado` (`PropiedadesDePrueba`).
  - `SupabaseFotoStorageTest` y `SecretosEnLogsTest` usan `MockRestServiceServer` con `proyecto-ficticio.supabase.co`.
  - `FotoStorageConfigTest` solo construye el bean.
  - La guarda de schema bloqueó, como corresponde, un contexto de prueba que arrancó sin perfil `test` en la copia.
- **Reloj:** el dominio y los servicios usan `RelojDePrueba` (2026-10-09). El `@Max(2027)` del DTO hace que los tests pasen en cualquier fecha, pero esconde B1. Ver también B7.
- **Huecos:**
  1. Concurrencia (A1).
  2. Rollback del alta si falla `registrarPrecio`.
  3. Subida a Storage correcta con falla al guardar en la base.
  4. Que el máximo de `anio` del DTO y el del dominio coincidan.
  5. Coerción de tipos numéricos (B3).

## Estado de los pendientes

Solo los que no tienen `[Resuelto …]` en §13.

| Etapa | Pendiente | Estado | Evidencia |
|---|---|---|---|
| 1 | Probar `cargar-env.ps1` en PowerShell | Vigente | No hay `pwsh` acá. Hay que hacerlo en Windows |
| 1 | `/swagger-ui.html` responde 302 | Vigente (es una nota, no una tarea) | Comportamiento de springdoc |
| 2 | `/actuator/health` incluye la base | Resuelto (decisión) | `ConfiguracionTransversalTest.healthIncluyeLaBaseDeDatos` |
| 2 → 9 | `curl` a una ruta inexistente con `X-Correlation-Id` | Vigente | Health y arranque sin clave hechos en la etapa 8. A10 del paso a paso no figura como corrido |
| 4 | `buscar` ordena por `creado_en DESC` | Resuelto | `VehiculoRepositorioJpaTest.buscarSinFiltro…`, descripción del `GET` |
| 4 → 9 | Lista de chequeo de la V1 sobre el contenedor | Resuelto | Etapa 8: health UP, 2 migraciones validadas. Consulta: `success = t` |
| 4 | Pruebas necesitan Docker; reiniciar el reloj | Vigente (nota) | Ver B7 |
| Seed → 9 | Repetir la lista de la etapa 5 sobre el contenedor; `flyway_schema_history` sin fallidas | Parcial | Sin filas fallidas: **resuelto** (consulta). Los conteos 10/8/2 **no valen** hoy (M1) |
| 5.1 | Corridas concurrentes de `-Psupabase` | Vigente | Sin lock (está en `mejoras.md`) |
| 5.1 | 6 contextos | Resuelto (vigente como nota) | Los surefire-reports muestran 6 `Started …` |
| 5.1 | Mensaje de "proyecto pausado" sin probar | Vigente | — |
| 6 | Año máximo `@Max(2027)` | Vigente | B1 |
| 6 → 9 | Repetir los casos 4 a 9 y 12 sobre el contenedor | Parcial | Etapa 8: 4 y 7; etapa 9: E5 a E8. No hay registro de 5, 6, 8, 9 ni 12 sobre el contenedor |
| 6 | Limpieza de `93a56a83` y del Corolla | Resuelto | `93a56a83` ya no existe. Los datos nuevos del Corolla están a propósito |
| 6 | Concurrencia "mismo microsegundo → 500" | **Desactualizado/incompleto** | El problema real es A1 |
| 7 → 9 | Casos 10 y 11 sobre el contenedor | Parcial | 10 hecho en la etapa 8. El 11 (PDF) no figura |
| 7 | `foto_url` de `93a56a83`, Fiesta y Sandero | Resuelto | Consulta: ninguna unidad con foto. Sandero con `actualizado_en` 2026-09-05. El Fiesta conserva `actualizado_en` del 10/10 (M1) |
| 8 | Limpiar `ec1b5aae` y su foto | Resuelto | No está en la tabla ni en el bucket |
| 8 | Ruta inexistente con `X-Correlation-Id` | Vigente | Igual que en la etapa 2 |
| 8 | `flyway_schema_history` sin filas fallidas | Resuelto | Consulta |
| 8 | Comentario de `application.yml` | Vigente | `application.yml:38` (B2) |
| 8 | Variables del shell y compose | Vigente (nota) | Ya está en las lecciones de `CLAUDE.md` |
| 8 | Flyway corre en el contenedor | Vigente | `application-docker.yml` no lo deshabilita (está en `mejoras.md`) |
| 8 | Host del pooler en el log | Vigente (aceptado) | — |
| 9 | Consigna E7 | Vigente | M2 |
| 9 | Repetir E7 (las dos variantes) y E1 en Postman | Vigente | Solo con `curl` |
| §15 | Avisar al front (`caja`, `/precio` con `disponible`) | Vigente | No hay evidencia en el repo |
| §15 | Coordinar con `calificacion` | Vigente | — |
| §15 | Ruta del gateway | Vigente | — |

Anotados en §13 (sección "Pendientes que dejó la auditoría final"): A1, M2, M3, M4, B1, B2, B6, B8 y los huecos de tests. M1 se descartó.

---

## Propuesto para `docs/mejoras.md`

No se escribió, porque la auditoría no modifica archivos. Texto listo para agregar al final:

```markdown
## 2026-10-11 · Auditoría final

- **Rechazar decimales y textos en campos enteros.** Jackson convierte `"anio": 2021.9` en 2021 y acepta `"anio": "2021"`. Mejora: `spring.jackson.deserialization.accept-float-as-int: false`. Impacto: mínimo (una línea y un test); el handler ya devuelve 422.
- **Validador de año con el `Clock`.** `@Max(2027)` en `AltaVehiculoRequest` se desalinea del Builder desde 2027. Mejora: constraint `@AnioDeModelo` que use el `Clock` inyectado. Impacto: bajo; solo importa si el servicio sigue después de 2026.
- **Romper el ciclo `api` ↔ `config`.** `WebConfig` importa el interceptor de `api.seguridad`, y `api` importa `CorrelationIdFilter` y `VehiculosProperties`. Mejora: mover `WebConfig` a `api.seguridad` (o el filtro y las constantes a un paquete compartido). Impacto: bajo; solo prolijidad.
- **`.gitignore` para variantes de `.env`.** Agregar `.env.*` y `!.env.example`. Impacto: mínimo.
- **Publicar el puerto solo en localhost.** `127.0.0.1:8083:8083` en el compose, si nadie de la red local lo necesita. Impacto: mínimo.
- **Pool de Hikari acotado contra el session pooler.** Hoy usa 10 conexiones por instancia (local, contenedor y tests contra el mismo proyecto gratuito). Mejora: `spring.datasource.hikari.maximum-pool-size: 5` en `local` y `docker`. Impacto: mínimo.
- **Reiniciar `RelojDePrueba` también en `@BeforeEach`** en `VehiculoEscrituraApiTest`. Impacto: mínimo; evita depender del orden de las clases.
```

---

## Resumen

**Bloquea la entrega**
1. A1, lost update que rompe el historial: bloqueo pesimista en las lecturas para escribir, más el test de carrera.
2. M2 y M3, corregir E7 del paso a paso y el orden de errores de §7; si no, la prueba manual "falla" con el código correcto.

**Puede esperar**
- M4: `CLAUDE.md` o README para el equipo (es tu decisión; conviene antes de entregar si lo leen otros).
- B1 a B9 y lo propuesto para `mejoras.md`.
- Pendientes manuales: `cargar-env.ps1` en Windows, A10, E7 y E1 en Postman, y el caso 11 sobre el contenedor.
