# Auditoría de `vehiculos` contra el Documento de arquitectura v1.1

- **Fecha:** 2026-10-11
- **Rama auditada:** `feature/vehiculos-auditoria` (HEAD `da5d11b`)
- **Referencia principal:** `docs/v1.1_Documento_de_arquitectura_CRM_leads.docx` (v1.1, octubre 2026). El texto se extrajo del XML del `.docx` (no hay `pandoc` instalado). Las figuras 3 (secuencia) y 5 (modelo de dominio) se leyeron como imagen (`word/media/image12.png` y `image13.png`).
- **Referencia secundaria:** `docs/arquitectura.md`, la especificación interna del servicio. Explica varias decisiones (DV-01 a DV-15), pero no reemplaza al documento del equipo: cuando difieren, gana el `.docx`.
- **Consumidores:** `calificacion`, `gateway`, `contracts/` e `infra/postgres/init.sql` no están en este repo ni en las carpetas vecinas. El frontend está en un repo hermano, `../DDA2` (rama `dev`, commit `d217077`), y se usó como evidencia.

---

## 1. Veredicto

El servicio está listo para la Primera Parte en lo que depende solo de él. La API, el modelo, los tres patrones, las capas, ProblemDetail, OpenAPI, Flyway y las pruebas cumplen, y el build pasa con 360 tests en verde y 96,7 % de cobertura de líneas. Los desvíos están en el despliegue: la base está en Supabase y no en `vehiculos_db` dentro del Postgres del compose común, y el servicio vive en un repo propio y no en el monorepo `crm-leads`. Ninguno rompe el flujo del MVP. El contrato con `calificacion` (`GET /vehiculos/{id}/precio`) no se puede verificar porque ese servicio no está disponible, y es el único punto que podría resultar bloqueante.

| Estado | Ítems |
|---|---|
| Cumple | 38 |
| Parcial | 8 |
| No cumple | 2 |
| Adelantado | 1 |
| No verificable | 5 |
| **Total** | **54** |

---

## 2. Tabla de cumplimiento

Las rutas de este repo son relativas a la raíz. `main/` abrevia `vehiculos-service/src/main/java/com/uade/crm/vehiculos/`, `res/` abrevia `vehiculos-service/src/main/resources/` y `test/` abrevia `vehiculos-service/src/test/java/com/uade/crm/vehiculos/`.

### A. Rol y alcance

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| A1 | Servicio de Inventario: unidades disponibles y precios | 8, 8.3 | Cumple | `main/api/VehiculoController.java:46` (`/vehiculos`). Endpoints 65-192: inventario, precio, historial y foto. Los extras están en la sección 4 | Documentar los extras |
| A2 | Datos propios en su base, sin planilla de Drive | 8.3 | Cumple | `res/db/migration/V1__vehiculo.sql:4-29`, `res/application.yml:31-35` (schema `vehiculos`). `grep -riE 'drive\|planilla\|sheets'` solo encuentra "Drive 1.3", la versión de un Fiat en el seed | — |
| A3 | Carga inicial por script | 5.1, 8.3 | Cumple | `res/db/seed/R__seed_vehiculos.sql:10-37` (10 unidades ficticias con su historial), activado por `res/application-seed.yml:4-6`. Nota: el contenedor fija `SPRING_PROFILES_ACTIVE: docker` (`infra/docker-compose.yml:12`), así que en Docker el seed no corre | Ver D-1 |
| A4 | Sin Kafka en la Primera Parte | 8.3 | Cumple | `grep -rliE kafka pom.xml vehiculos-service infra Dockerfile` no encuentra nada. No hay dependencias de mensajería en `vehiculos-service/pom.xml:17-77` | — |
| A5 | Gestión de stock desde Drive fuera de alcance | 4.3 | Cumple | No hay nada implementado (mismo `grep` que A2) | — |

### B. Requerimientos funcionales

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| B1 | RF-05: precio de la unidad al calificar | 5.2 | Cumple | `main/api/VehiculoController.java:90-102`, `main/api/dto/PrecioResponse.java:10-14`. El consumo real está en K1 | — |
| B2 | RF-11: alta, consulta y actualización de precio y disponibilidad | 5.2 | Cumple | Alta: `VehiculoController.java:117-138`. Consulta: 65-88. Actualización: 140-158 y `main/application/ActualizacionVehiculoService.java:27-49` | — |
| B3 | RF-01: lista de disponibles para el selector | 5.2, 8.3 | Cumple | `VehiculoController.java:65-75` con `?disponible=`, `main/infrastructure/persistence/VehiculoRepositorioJpa.java:47-52` | — |

### C. API REST

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| C1 | `GET /vehiculos?disponible=true` | 8.3 | Cumple | `VehiculoController.java:65-75`. Responde 200 con una lista de `VehiculoResponse`. Sin el parámetro devuelve todas las unidades | — |
| C2 | `GET /vehiculos/{id}` | 8.3 | Cumple | `VehiculoController.java:77-88`. Responde 200 aunque `disponible=false` | — |
| C3 | `GET /vehiculos/{id}/precio` | 8.3 | Cumple | `VehiculoController.java:90-102`. Respuesta `{"precio":11500000.00,"disponible":false}` (`test/api/VehiculoApiSeedTest.java:93`) | — |
| C4 | `POST / PUT /vehiculos` | 8.3 | Cumple | `POST /vehiculos` responde 201 con `Location` (`VehiculoController.java:117-138`). `PUT /vehiculos/{id}` solo cambia `precio` y/o `disponible` (140-158). El documento es ambiguo (ver sección 5) | Cerrar la ambigüedad en el documento |
| C5 | DTOs, no entidades JPA | 10.1 | Cumple | Records en `main/api/dto/` (`VehiculoResponse.java:12-36`). `VehiculoEntity` es de paquete (`main/infrastructure/persistence/VehiculoEntity.java:30`) y `creadoEn`/`actualizadoEn` no se exponen (`VehiculoApiSeedTest.java:88`) | — |
| C6 | Validación de entrada con Bean Validation | 8.3 | Parcial | Las anotaciones están en `main/api/dto/AltaVehiculoRequest.java:18-25` y `ActualizacionVehiculoRequest.java:11-16`, con 422 y `errores` (`main/api/error/ApiExceptionHandler.java:68-95`). Desvío: el tope `@Max(2027)` está fijo (`AltaVehiculoRequest.java:21`), mientras el dominio usa el año actual + 1 (`main/domain/model/Vehiculo.java:213`). Desde el 2027-01-01 las dos reglas difieren | Ver D-5 |
| C7 | Id inexistente | 8.3 | Cumple | Responde 404 `VEHICULO_NO_ENCONTRADO` en ProblemDetail (`ApiExceptionHandler.java:152-155`). Tests en `test/api/VehiculoControllerTest.java:195-215`. Un id que no es UUID da 400 `SOLICITUD_INVALIDA` | — |

### D. Modelo de dominio

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| D1 | Campos de la figura 5 (marca, modelo, `int anio`, `int kilometraje`, precio, `Caja caja`, `fotoUrl` opcional, `boolean disponible`) | Fig. 5, 8.3 | Cumple | `main/domain/model/Vehiculo.java:21-30`. `Caja` es un enum (`Caja.java:4-7`) y `fotoUrl` es `Optional` (86-88). Extra: `version` opcional (ver sección 4) | Agregar `version` a la figura 5 |
| D2 | `Money precio`, nunca `double`/`float` | Fig. 5 | Cumple | `BigDecimal` normalizado a escala 2 y validado (`Vehiculo.java:109-121`), columna `NUMERIC(14,2)` (`V1__vehiculo.sql:11`). `grep -rE '\bdouble\b\|\bfloat\b'` solo encuentra `accept-float-as-int` en `application.yml:24`. No hay value object `Money` ni moneda: el peso es implícito | Cerrar la ambigüedad (sección 5) |
| D3 | Relaciones con otros servicios solo por id | 10.2 | Cumple | `V1__vehiculo.sql` no referencia tablas de otros servicios. La única FK es interna: `precio_historial.vehiculo_id → vehiculo.id` (línea 24) | — |
| D4 | Todo por UUID (AD-13) | AD-13 | Cumple | `UUID id` en `Vehiculo.java:21`, `V1__vehiculo.sql:5,23`. Rutas con `@PathVariable UUID id` (`VehiculoController.java:86,100`) | — |

### E. Patrones de diseño

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| E1 | Repository con Spring Data JPA, aislando al dominio | 11, AD-15 | Cumple | Puerto `main/domain/port/VehiculoRepositorio.java:16-42`, adaptador `main/infrastructure/persistence/VehiculoRepositorioJpa.java:29-30` con mapeo manual (98-124) y Spring Data `VehiculoJpaRepository.java:13-19`. Está bien aplicado: el dominio solo conoce el puerto | — |
| E2 | Builder `Vehiculo.builder()` que valida en `build()` precio positivo y año razonable | 11 | Cumple | Builder escrito a mano, sin Lombok (`Vehiculo.java:129-227`). `build()` valida marca, modelo, año entre 1950 y el actual + 1, kilometraje ≥ 0, precio > 0 con 2 decimales y 12 enteros, y `caja` presente (200-226). Lo cubren 45 casos en `test/domain/model/VehiculoBuilderTest.java`. Diferencia nominal: la firma es `builder(Clock)` (46-48), para poder testear el año | — |
| E3 | Strategy `FotoStorage` con implementaciones intercambiables | 11 | Cumple | Interfaz `main/domain/port/FotoStorage.java:7-12`. Implementaciones `SupabaseFotoStorage.java:21` y `FotoStorageDeshabilitado.java:9-21`, elegidas por configuración en `FotoStorageConfig.java:20-26`. El patrón es real: el caso de uso (`FotoVehiculoService.java:24,37`) no sabe cuál usa. La segunda estrategia es un Null Object que siempre responde 503 | — |

Otros patrones presentes: Adapter / Ports & Adapters (`VehiculoRepositorioJpa`, `SupabaseFotoStorage`), Null Object (`FotoStorageDeshabilitado`), una factory simple por `switch` (`FotoStorageConfig.java:22-25`), Chain of Responsibility de servlet (`main/config/CorrelationIdFilter.java:22-24`) e Interceptor (`main/api/seguridad/ClaveServicioInterceptor.java:13`).

### F. Arquitectura en capas

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| F1 | Paquetes `api`, `application`, `domain` (`model`, `port`), `infrastructure` (`persistence`, adaptadores), `config` | 10.1 | Cumple | Árbol `main/` (`git ls-files vehiculos-service/src`). Hay un `package-info.java` por paquete | — |
| F2 | El dominio no importa Spring Data, JPA, HTTP ni Kafka | 10.1 | Cumple | `grep -rn '^import' main/domain \| grep -v 'import java\.' \| grep -v 'com.uade.crm.vehiculos.domain'` no encuentra nada | — |
| F3 | Entidad de dominio separada de la entidad JPA | 10.1 | Cumple | `Vehiculo` (POJO, `Vehiculo.java:14`) y `VehiculoEntity` (`@Entity`, `VehiculoEntity.java:28-30`) son clases distintas | — |

### G. Requerimientos no funcionales

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| G1 | RNF-06: ProblemDetail con manejador global | 13.1 | Cumple | `@RestControllerAdvice` que extiende `ResponseEntityExceptionHandler` (`ApiExceptionHandler.java:55-56`), con `codigo`, `correlationId` y `errores` (193-214) y catálogo en `CodigoError.java:13-21`. Tests en `test/api/error/ApiExceptionHandlerTest.java` | — |
| G2 | RNF-07: configuración externalizada por variables y perfiles | 13.1 | Cumple | Variables `VEHICULOS_*` sin valor por defecto (`res/application.yml:43-48`, `application-local.yml:5-8`, `application-docker.yml:4-7`), `@ConfigurationProperties` validado (`main/config/VehiculosProperties.java:22-39`). `infra/.env.example` solo tiene placeholders | — |
| G3 | RNF-05: leer el correlationId y llevarlo a los logs | 13.1, AD-10 | Cumple | `CorrelationIdFilter.java:31-45` toma `X-Correlation-Id` o genera uno, lo pone en el MDC y lo devuelve. Patrón de log en `res/logback-spring.xml:6-7`. El nombre del header que usa el gateway está en L2 | Confirmar el header con el gateway |
| G4 | RNF-08: levantar con `docker compose up` junto al resto | 13.1, 13.3 | Parcial | `infra/docker-compose.yml:2-28` levanta solo `vehiculos`, sin red compartida, y exige credenciales de Supabase e internet (líneas 13-18). No hay un compose común | Ver D-1 |
| G5 | OpenAPI con springdoc desde la Primera Parte | 8, 13.2 | Cumple | `vehiculos-service/pom.xml:47-50`, `res/application.yml:60-64`, `main/config/OpenApiConfig.java:19-32`. Todas las respuestas están documentadas (`VehiculoController.java:67-187`) | — |
| G6 | Actuator con health | 8.5 (Segunda) | Adelantado | `vehiculos-service/pom.xml:31-33`, `res/application.yml:50-58` (solo `health`, con probes). Lo usa el healthcheck del compose (`infra/docker-compose.yml:22-23`) | — |

### H. Tecnología

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| H1 | Java 21, Spring Boot 3 | 13.2 | Cumple | `pom.xml:10` (Boot 3.5.16) y `pom.xml:25` (Java 21) | — |
| H2 | PostgreSQL 16 | 13.2 | Parcial | En ejecución la base es el Postgres gestionado de Supabase, cuya versión no figura en el repo. Los tests usan `postgres:17-alpine` (`test/PostgresDePrueba.java:25`) | Ver D-1 |
| H3 | Spring Data JPA y Flyway, sin `ddl-auto` update/create | 13.2 | Cumple | `vehiculos-service/pom.xml:26-29,34-41`, `res/application.yml:28` (`ddl-auto: validate`) y 32-39 (Flyway) | — |
| H4 | springdoc-openapi | 13.2 | Cumple | Igual que G5 (`pom.xml:27`, versión 2.9.1) | — |
| H5 | JUnit 5, Mockito, Testcontainers | 13.2 | Cumple | `vehiculos-service/pom.xml:52-76`. Mockito se usa en 9 clases (`grep -rl org.mockito`). Testcontainers con `@ServiceConnection` en `test/PostgresDePrueba.java` | — |
| H6 | Maven multi-módulo y Docker | 13.2 | Parcial | Hay POM padre `crm-parent`, pero con un único módulo (`pom.xml:20-22`). Docker: ver I5 | Ver D-2 |

### I. Despliegue e infraestructura

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| I1 | `vehiculos-service/` dentro del monorepo `crm-leads` con POM padre | 14.1, AD-08 | No cumple | El repo es propio (`VehiculosMicroservice`) y no tiene `contracts/`, `gateway/`, `infra/postgres/`, `frontend/` ni otros servicios (`git ls-files`). El frontend también está en un repo aparte (`../DDA2`, remoto `manuherrera09/DDA2`) | Ver D-2 |
| I2 | Base `vehiculos_db` en la única instancia de PostgreSQL, creada por `infra/postgres/init.sql`, sin acceso de otros servicios | 13.3, AD-02 | No cumple | No hay `infra/postgres/init.sql` (`ls infra` muestra solo `docker-compose.yml`). La base es el schema `vehiculos` en Supabase (`res/application.yml:31,35`, `infra/.env.example:1-3`). Que ningún otro servicio acceda a ella no se puede verificar: depende de los permisos del proyecto Supabase | Ver D-1 |
| I3 | Puerto interno 8081–8084, no expuesto fuera de Docker | 13.3 | Parcial | El puerto 8083 está en rango (`res/application.yml:2`, `Dockerfile:18`), pero se publica al host (`infra/docker-compose.yml:8-9`) | Ver D-3 |
| I4 | Comunicación entre contenedores por nombre | 13.3 | No verificable | `vehiculos` no llama a otros contenedores, y su compose no declara red compartida. Hace falta el compose común con `gateway` y `calificacion` | Revisar con el compose común |
| I5 | Dockerfile propio | 13.3 | Cumple | `Dockerfile:1-19`: multi-stage, JRE 21 y usuario sin privilegios (17). `.dockerignore` con lista blanca | — |

### J. Pruebas

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| J1 | Tests unitarios del Builder | 11, 13.2 | Cumple | `test/domain/model/VehiculoBuilderTest.java` (45 casos), `VehiculoMutadoresTest.java` (12) | — |
| J2 | Tests del servicio de aplicación | 13.2 | Cumple | `test/application/*ServiceTest.java`: 4 clases, 38 casos, con Mockito | — |
| J3 | Tests de controllers | 13.2 | Cumple | `@WebMvcTest` (`test/PruebaWebMvc.java`): `VehiculoControllerTest` (15), `VehiculoControllerEscrituraTest` (67), `VehiculoControllerFotoTest` (7), `ClaveServicioTest` (20) | — |
| J4 | Integración con Testcontainers y PostgreSQL | 13.2 | Cumple | `@SpringBootTest(RANDOM_PORT)` (`test/PruebaIntegracion.java`) sobre `postgres:17-alpine`. Incluye `MigracionesTest`, `VehiculoRepositorioJpaTest`, `CarreraDePutsTest` y `VehiculoApiSeedTest`, entre otros | — |

Cobertura aproximada (JaCoCo 0.8.13, ejecutado desde la línea de comandos sin tocar el POM):

| Capa | Líneas | Ramas |
|---|---|---|
| `domain` | 100,0 % (141/141) | 100,0 % (54/54) |
| `application` | 100,0 % (80/80) | 100,0 % (14/14) |
| `config` | 100,0 % (37/37) | 93,8 % (15/16) |
| `infrastructure` | 97,5 % (159/163) | 94,4 % (17/18) |
| `api` | 91,7 % (144/157) | 77,3 % (51/66) |
| **Total** | **96,7 % (562/581)** | **89,9 % (151/168)** |

### K. Contrato con los consumidores

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| K1 | `calificacion` consume `GET /vehiculos/{id}/precio`: ruta, JSON y tipo del precio | 9.2 (fig. 3, pasos 11-12) | No verificable | Lado `vehiculos`: responde `{"precio":11500000.00,"disponible":false}`, con `precio` como número JSON de 2 decimales (`VehiculoApiSeedTest.java:93`). La figura 3 espera `200 { precio }`. No están `calificacion-service`, `VehiculosPort` ni `VehiculosRestClient` | Ver sección 6 y plan, paso 1 |
| K2 | Frontend: `GET /api/vehiculos?disponible=true`, con los campos que muestra | 8.3, RF-01 | Parcial | El front todavía usa un mock (`../DDA2/src/views/PrequalificationView.jsx:12,125`, `src/data/vehicles.js:1-14`). La ruta base sí coincide (`src/api/axiosClient.js:4` + `src/utils/constants.js:10` = `/api/vehiculos`). Todos los campos que muestra existen con otro nombre (sección 6). Diferencia de valores: el front filtra la caja por `Automática`/`Manual` (`src/components/VehicleFilters.jsx:30-34`, `VehicleSelection.jsx:14`, `docs/contratos-backend.md:60`) y la API devuelve `AUTOMATICA`/`MANUAL` (`main/domain/model/Caja.java:5-6`) | Ver D-4 |
| K3 | Precio de una unidad no disponible o inexistente, y si `calificacion` lo maneja | 9.2 | No verificable | Lado `vehiculos`: si no está disponible responde 200 con `disponible:false` (`VehiculoController.java:91-92`). Si no existe, 404 `VEHICULO_NO_ENCONTRADO` en `application/problem+json` (`VehiculoControllerTest.java:204-206`). Cómo lo trata `calificacion` no se puede verificar | Plan, paso 1 |

### L. Gateway

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| L1 | Ruta `/api/vehiculos/**` con reescritura del prefijo | 8, 14.3 | No verificable | El gateway no está en el repo. `docs/arquitectura.md` §15 propone `Path=/api/vehiculos/**` con `StripPrefix=1` hacia `http://vehiculos:8083` | Plan, paso 3 |
| L2 | Filtro de correlationId (Chain of Responsibility) y CORS centralizado | 11, AD-10 | No verificable | El gateway no está en el repo. `vehiculos` no configura CORS, lo cual es correcto según AD-10 (`grep -riE 'cors\|CrossOrigin'` no encuentra nada en `main/`) | Plan, paso 3 |

### M. Convenciones de equipo

| ID | Requisito | Sección | Estado | Evidencia | Acción |
|---|---|---|---|---|---|
| M1 | Ramas `feature/vehiculos-{tema}` | 14.2 | Parcial | De 12 ramas mergeadas por PR, solo 3 siguen el formato (`git log --merges`). Las demás son `feature/esqueleto`, `feature/dominio`, `feature/docker`, etc. | Ninguna: desvío ya aceptado por el responsable |
| M2 | Commits `tipo(vehiculos): descripción` | 14.2 | Parcial | 5 de 39 commits siguen el formato (`git log --all --format=%s \| grep -cE '^[a-z]+\(vehiculos\): '`) | Ninguna: desvío ya aceptado |
| M3 | `main` protegida y cambios por PR | 14.2 | Cumple | 12 merges de PR (#1 a #12) en `git log`. La protección de la rama no se pudo consultar (sin `gh` ni API de GitHub) | — |

---

## 3. Desvíos priorizados

### Bloqueantes

No hay ninguno confirmado. El único candidato es K1: si `calificacion` espera otra forma del JSON, otro tipo de precio o falla con campos desconocidos, el flujo del MVP se corta en el paso 11 de la figura 3. Queda como **No verificable** y es la primera acción del plan.

### Importantes

**D-1. La base y el despliegue no siguen el compose común (I2, G4, H2).**
- **Qué dice el plan:** una sola instancia de PostgreSQL 16 con una base por servicio creada por `infra/postgres/init.sql` (AD-02, 13.2, 13.3), y todo el sistema se levanta con `docker compose up` en una máquina limpia (RNF-08).
- **Qué hace el código:** usa el schema `vehiculos` de un Postgres de Supabase por session pooler (`res/application.yml:31,35`, `infra/.env.example:1-3`, decisión DV-01 en `docs/arquitectura.md`). El compose tiene solo este servicio y exige credenciales (`infra/docker-compose.yml:13-18`). Los tests usan Postgres 17. En Docker no se carga el seed (`infra/docker-compose.yml:12`).
- **Por qué importa:** la demo depende de internet, de credenciales compartidas y de que el proyecto Supabase no esté pausado (se pausa tras una semana sin actividad). En una máquina limpia no se cumple RNF-08, y la cátedra evalúa el despliegue.
- **Cambio sugerido:** agregar un perfil (por ejemplo `compose`) con `jdbc:postgresql://postgres:5432/vehiculos_db`, sumar `CREATE DATABASE vehiculos_db` y el rol al `init.sql` común, activar `seed` en ese perfil y usar `VEHICULOS_FOTOS_MODO=deshabilitado`. El front ya muestra iniciales si falta la foto (`../DDA2/src/components/VehicleThumb.jsx:6-12`). Supabase puede quedar como perfil alternativo. Si el equipo prefiere mantener Supabase, registrar en el documento una AD nueva que reemplace AD-02 para `vehiculos`. Flyway con `schemas: vehiculos` funciona igual sobre `vehiculos_db`.

**D-2. El servicio no está en el monorepo `crm-leads` (I1, H6).**
- **Qué dice el plan:** monorepo Maven multi-módulo con `contracts/`, servicios, `frontend/`, `infra/` y `docs/` (14.1, AD-08).
- **Qué hace el código:** un repo propio con POM padre `crm-parent` y un único módulo (`pom.xml:14-22`). El frontend también está en otro repo.
- **Por qué importa:** 13.2 pide Maven multi-módulo, y AD-08 se justifica para que productor y consumidor compilen contra el mismo contrato. `vehiculos` no consume ni publica eventos, así que el costo técnico de estar afuera es bajo, pero el desvío es visible en la entrega.
- **Cambio sugerido:** es una decisión de equipo. Se puede mover `vehiculos-service/` al monorepo (el `groupId` `com.uade.crm` y el `artifactId` `crm-parent` ya coinciden, así que alcanza con agregarlo como módulo) o registrar una AD nueva de "un repo por servicio" que reemplace AD-08.

### Menores

**D-3. El puerto está publicado al host (I3).**
- **Plan:** solo frontend y gateway exponen puertos (13.3).
- **Código:** `infra/docker-compose.yml:8-9` publica `8083:8083`.
- **Por qué importa:** en el entorno integrado deja a `vehiculos` accesible sin pasar por el gateway. Para desarrollo aislado es útil.
- **Cambio:** en el compose común usar `expose: ["8083"]` y dejar `ports` en un override de desarrollo.

**D-4. Integración del front pendiente y valores de `caja` distintos (K2).**
- **Plan:** el front usa la lista de disponibles (RF-01, 8.3). El documento no define formatos.
- **Código:** la API devuelve `caja` como `AUTOMATICA`/`MANUAL` (DV-04). El contrato del front espera `Automática`/`Manual` y lo marca como "[A confirmar]" (`../DDA2/docs/contratos-backend.md:60`).
- **Por qué importa:** si el front adapta solo los nombres de los campos, el filtro de caja deja de encontrar unidades.
- **Cambio:** avisar al front (el pendiente ya figura en `docs/arquitectura.md` §15) para que mapee los valores en su adaptador. No hace falta cambiar `vehiculos`.

**D-5. Tope de año fijo en el DTO (C6).**
- **Plan:** el Builder valida un "año razonable" (11).
- **Código:** el dominio usa el año actual + 1 (`Vehiculo.java:212-219`), pero `AltaVehiculoRequest.java:21` fija `@Max(2027)`.
- **Por qué importa:** desde el 2027-01-01 una unidad 2028 sería válida para el dominio y rechazada por el DTO, y además se rompe la regla de no hardcodear valores (RNF-07).
- **Cambio:** quitar `@Max` del DTO y dejar que el dominio devuelva 422 `VALIDACION` (ya lo hace con `DatosInvalidosException`, `AltaVehiculoService.java:45-46`), o reemplazarlo por un validador que use el `Clock`. Hay que ajustar los tests de `VehiculoControllerEscrituraTest` que esperan el mensaje de `@Max`.

**D-6. Convenciones de ramas y commits (M1, M2).** Están en la tabla como observación. El responsable del servicio ya aceptó este desvío y no hace falta corregir el historial.

---

## 4. Lo que el código tiene y el plan no

| Extra | Evidencia | Recomendación |
|---|---|---|
| Campo `version` (opcional) | `Vehiculo.java:24,62-64`, `V1__vehiculo.sql:8`, `VehiculoResponse.java:16` | Mantener y agregarlo a la figura 5 y a 8.3. La sección 11 ya lo nombra, y el front lo usa como `detail` |
| Historial de precios: tabla `precio_historial` y `GET /vehiculos/{id}/historial-precios` | `V1__vehiculo.sql:22-29`, `VehiculoController.java:104-115`, `AltaVehiculoService.java:29`, `ActualizacionVehiculoService.java:45-47` | Mantener (costo bajo y bien probado) y documentar en 8.3. Sirve para auditar la cobertura que calculó `calificacion` |
| Carga de foto `PUT /vehiculos/{id}/foto` (multipart, JPEG/PNG/WebP de hasta 2 MB, detección por firma), con borrado de la foto anterior | `VehiculoController.java:160-192`, `main/domain/model/ArchivoFoto.java`, `TipoFoto.java`, `FotoVehiculoService.java:34-43` | Mantener: le da un uso real al Strategy `FotoStorage`. Documentar el endpoint en 8.3 |
| `disponible` en la respuesta de `/precio` | `PrecioResponse.java:10` | Mantener y documentarlo en 8.3 y en la figura 3 (`200 { precio, disponible }`). Coordinar con `calificacion` |
| Clave de servicio `X-Api-Key` en las escrituras | `main/api/seguridad/ClaveServicioInterceptor.java:24-30`, `RequiereClaveServicio`, `VehiculosProperties.java:35-39` | Mantener. No es autenticación de usuarios (4.3), pero el documento debería mencionarla porque afecta al gateway (tiene que reenviar el header) y a quien cargue unidades |
| Bloqueo pesimista en las escrituras (`SELECT … FOR UPDATE`) | `VehiculoJpaRepository.java:17-18`, `test/api/CarreraDePutsTest.java` | Mantener. Es un detalle interno y no requiere documentación |
| Jackson estricto: rechaza campos desconocidos y coerciones | `res/application.yml:19-24` | Mantener. Afecta solo a quien escribe. Documentarlo en el contrato |
| Catálogo de códigos de error propios (`codigo`, `type` estable) | `CodigoError.java:13-21` | Mantener. Conviene que el equipo lo adopte como convención común |
| Suite opcional contra Supabase (`-Psupabase`, schema `vehiculos_test`) | `vehiculos-service/pom.xml:88-123`, `test/resources/application-supabase.yml` | Mantener mientras la base siga en Supabase. Con D-1 pierde sentido |
| Probes de health (liveness y readiness) y healthcheck del contenedor | `res/application.yml:55-58`, `infra/docker-compose.yml:22-28` | Mantener. Lo va a usar el monitor de la Segunda Parte |
| Colección de Postman | `docs/Servicio de Vehículos.postman_collection.json` | Mantener. Adelanta las "evidencias de prueba" de la Segunda Parte (4.2) |
| Listado sin `disponible`: devuelve todas, ordenadas por `creadoEn` descendente | `VehiculoRepositorioJpa.java:33,47-52` | Mantener. El plan no define orden |

No hay dependencias extra respecto del plan. Supabase Storage se usa con el `RestClient` de Spring, sin SDK.

---

## 5. Ambigüedades del plan

| # | Ambigüedad | Qué decidió el código | Propuesta para la v1.2 |
|---|---|---|---|
| 1 | La sección 11 nombra `version` como opcional, pero la figura 5 y 8.3 no la incluyen | `version` es un `String` opcional (`Vehiculo.java:24`) | Agregar `String version` (opcional) a la figura 5 y a 8.3 |
| 2 | `POST / PUT /vehiculos` no aclara si la actualización es `PUT /vehiculos/{id}` ni si hay endpoint propio para precio o disponibilidad | `PUT /vehiculos/{id}` con `precio` y/o `disponible` (al menos uno); el resto es inmutable (DV-06) | Reemplazar la fila por `POST /vehiculos` (201) y `PUT /vehiculos/{id}` (precio y/o disponibilidad) |
| 3 | `Money precio` sin tipo concreto ni moneda | `BigDecimal` con escala 2, `NUMERIC(14,2)`, en pesos sin declararlo | Fijar "`BigDecimal`, 2 decimales, ARS implícito" o agregar un campo `moneda` |
| 4 | No define "año razonable" ni dónde se configura | De 1950 al año actual + 1, fijo en código (`Vehiculo.java:16,213`), con el tope duplicado en el DTO (D-5) | Escribir la regla en 8.3 y decidir si va a configuración |
| 5 | No define qué implementaciones de `FotoStorage` hay en la Primera Parte | `SupabaseFotoStorage` y `FotoStorageDeshabilitado`. El contrato del front proponía `LocalFotoStorage` (`../DDA2/docs/contratos-backend.md:77`) | Nombrar las dos implementaciones y cuál se usa en el compose del laboratorio |
| 6 | 4.2 ubica "Contratos REST documentados con OpenAPI" en la Segunda Parte, pero la sección 8 lo exige desde la Primera | springdoc desde la Primera Parte | Unificar en "desde la Primera Parte" |
| 7 | "Se expone bajo `/api/{servicio}`" no dice si el gateway quita el prefijo | El servicio expone `/vehiculos/...` y asume `StripPrefix=1`. El front asume `/api/vehiculos` (`../DDA2/docs/contratos-backend.md:35`) | Escribir la regla de ruteo en 8 o 13.3 |
| 8 | No define qué devuelve `/precio` si la unidad no está disponible o no existe | 200 con `disponible:false`, o 404 ProblemDetail (DV-07) | Documentarlo en 8.3 y definir qué hace `calificacion` en cada caso |
| 9 | La autenticación queda fuera de la Primera Parte, pero no se dice quién puede escribir en el inventario | `X-Api-Key` en las escrituras; lecturas abiertas | Mencionar la clave de servicio y quién la tiene |
| 10 | No se nombra el header del correlationId | `X-Correlation-Id` | Fijar el nombre en AD-10 |
| 11 | "Carga inicial por script" no dice qué script ni cuándo corre | Migración repetible de Flyway, solo con el perfil `seed` | Definir si el seed corre en el compose del laboratorio |
| 12 | No define orden ni paginación del listado | Sin paginación, por `creadoEn` descendente | Aclarar que el front filtra en memoria y que no hay paginación |
| 13 | AD-02 y 13.3 suponen PostgreSQL local; el plan no contempla bases gestionadas | Supabase (DV-01) | Resolver con D-1 |

---

## 6. Impacto en otros servicios

**`calificacion`**
- Ruta interna: `GET http://vehiculos:8083/vehiculos/{id}/precio`. Es abierta y no necesita `X-Api-Key`.
- Respuesta 200: `{"precio": 11500000.00, "disponible": false}`. `precio` es un número JSON con 2 decimales y debe mapearse a `BigDecimal`, nunca a `double`. El campo `disponible` es nuevo respecto de la figura 3: si el cliente deserializa con `FAIL_ON_UNKNOWN_PROPERTIES=true`, va a fallar.
- Responde 200 aunque la unidad no esté disponible: `calificacion` decide si califica igual.
- Un id inexistente da 404 `application/problem+json` con `codigo: VEHICULO_NO_ENCONTRADO`. Un id que no es UUID da 400. Hay que definir si ese lead va a la DLT o se califica sin precio.
- Conviene propagar `X-Correlation-Id` en la llamada REST para que el flujo se pueda seguir en los logs (RNF-05).

**Frontend**
- Correspondencia de campos: `brand→marca`, `model→modelo`, `year→anio`, `km→kilometraje`, `price→precio`, `transmission→caja`, `detail→version`, `fotoUrl→fotoUrl`, `id→id`.
- `caja` llega como `AUTOMATICA` o `MANUAL` y hay que mapearla a `Automática` o `Manual`.
- `version` y `fotoUrl` pueden llegar en `null`.
- `id` es un UUID y no un slug.
- `fotoUrl` apunta a la CDN pública de Supabase, no al gateway. El front ya tolera que falte.
- `GET /api/vehiculos?disponible=true` trae la lista completa, sin paginar.

**Gateway**
- Ruta: `Path=/api/vehiculos/**`, `StripPrefix=1`, hacia `http://vehiculos:8083`.
- Tiene que reenviar los headers `X-Api-Key` (escrituras) y `X-Correlation-Id`.
- El CORS va en el gateway: `vehiculos` no lo configura.
- Si el gateway documenta las APIs, Swagger de `vehiculos` está en `/v3/api-docs`.

**Monitor (Segunda Parte, opcional)**
- `GET /actuator/health` ya está expuesto, con probes de liveness y readiness.

---

## 7. Plan de acción

| # | Acción | Ítems | Esfuerzo |
|---|---|---|---|
| 1 | Revisar con el responsable de `calificacion` su `VehiculosRestClient`: ruta, tipo `BigDecimal`, tolerancia a `disponible` y manejo de 404 y no disponible. Es el único bloqueante posible | K1, K3 | S |
| 2 | Definir la base del laboratorio: perfil `compose` con `vehiculos_db` en el Postgres común, seed activo y fotos deshabilitadas, o una AD nueva que acepte Supabase | I2, G4, H2 | M |
| 3 | Acordar con el gateway la ruta con `StripPrefix=1` y el reenvío de `X-Api-Key` y `X-Correlation-Id` | L1, L2, G3 | S |
| 4 | Pasar al front la correspondencia de campos y los valores de `caja` | K2 | S |
| 5 | Decidir en equipo entre monorepo y repos separados, y aplicarlo o registrarlo como AD | I1, H6 | M (mover) / S (AD) |
| 6 | Quitar `@Max(2027)` de `AltaVehiculoRequest` y ajustar sus tests | C6 | S |
| 7 | En el compose común, usar `expose` en vez de `ports` | I3 | S |
| 8 | Actualizar el documento a la v1.2 con las ambigüedades de la sección 5 y los extras de la sección 4 | — | S |

---

## 8. Cómo se verificó

**Comandos ejecutados (todos de solo lectura sobre el código):**
- `git ls-files`, `git branch -a`, `git log --all --format=...` y `git log --merges` para mapear el repo, las ramas y los commits.
- Extracción del `.docx`: `unzip` del archivo en el scratchpad y un script Python sobre `word/document.xml`. Las figuras 3 y 5 se leyeron como imagen.
- Lectura completa de `vehiculos-service/src/main`, de los POM, del compose, del Dockerfile, de `.dockerignore`, de `.env.example`, de las migraciones y del seed.
- `grep -rn '^import' …/domain | grep -v 'import java\.' | grep -v '…domain'` para verificar que el dominio no tenga dependencias de framework (resultado vacío).
- `grep -rliE 'kafka|lombok|\bdouble\b|\bfloat\b|drive|planilla|sheets' pom.xml vehiculos-service infra Dockerfile` (solo dos falsos positivos: `accept-float-as-int` en `application.yml` y "Drive 1.3" en el seed).
- `mvn -pl vehiculos-service -am verify` (Docker 29.8.2, OpenJDK 21.0.12, Maven 3.9.16): **BUILD SUCCESS, 360 tests, 0 fallos, 0 errores, 0 omitidos**, en 22 s. Los `ERROR` del log son de tests que prueban caminos de error a propósito.
- `mvn -q -pl vehiculos-service -am org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent verify org.jacoco:jacoco-maven-plugin:0.8.13:report`: termina bien. La cobertura de la sección 2.J sale de `vehiculos-service/target/site/jacoco/jacoco.csv`.
- Lectura del frontend hermano `../DDA2` (rama `dev`): `docs/contratos-backend.md`, `src/api`, `src/utils/constants.js`, `src/data/vehicles.js` y los componentes del selector.

**Lo que quedó sin verificar:**
- `calificacion-service` (`VehiculosPort`, `VehiculosRestClient`), `gateway/`, `contracts/`, `infra/postgres/init.sql` y el compose común: no están en el repo ni en las carpetas vecinas, y no hay `gh` instalado para buscarlos en GitHub.
- La versión de Postgres de Supabase y quién más accede al proyecto.
- La protección de la rama `main` en GitHub.
- El arranque real con `docker compose up`: no se ejecutó porque necesita las credenciales de Supabase de `infra/.env`.
- La suite `-Psupabase`: no se corrió, por el mismo motivo.
