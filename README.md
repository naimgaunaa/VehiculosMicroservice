# VehiculosMicroservice

Microservicio `vehiculos` del CRM de una agencia de autos usados (TP de Desarrollo de Aplicaciones II, UADE). Administra el inventario: alta de unidades, precio y disponibilidad, historial de precios y foto. Corre en el puerto **8083**.

La base de datos no es local: es el schema `vehiculos` de un PostgreSQL en **Supabase** que comparte todo el equipo, y las fotos van a **Supabase Storage**. Por eso, para levantar el servicio vas a necesitar algunas claves que te pasa el responsable (ver [Claves que tenés que pedir](#1-claves-que-tenés-que-pedir)).

**Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL (Supabase), Supabase Storage, springdoc-openapi, JUnit 5, Mockito, Testcontainers, Maven y Docker Compose.

## Índice

1. [Claves que tenés que pedir](#1-claves-que-tenés-que-pedir)
2. [Requisitos](#2-requisitos)
3. [Paso a paso con Docker (recomendado)](#3-paso-a-paso-con-docker-recomendado)
4. [Paso a paso con Maven, sin Docker](#4-paso-a-paso-con-maven-sin-docker)
5. [Comprobar que funciona](#5-comprobar-que-funciona)
6. [Correr los tests](#6-correr-los-tests)
7. [API](#7-api)
8. [Problemas frecuentes](#8-problemas-frecuentes)
9. [Reglas para no romper la base compartida](#9-reglas-para-no-romper-la-base-compartida)

---

## 1. Claves que tenés que pedir

Las pasa el responsable de `vehiculos` (Naim Gauna), **solo por mensaje directo o gestor de contraseñas**. Nunca por un commit, un issue, un grupo ni una captura de pantalla. Las claves se rotan al terminar la entrega.

### Según lo que vayas a hacer

| Querés… | Tenés que pedir |
|---|---|
| Correr los tests (`mvn verify`) | **Nada.** Los tests levantan su propio PostgreSQL con Docker |
| Consumir la API de un `vehiculos` que ya está corriendo (desde `calificacion`, el front o el gateway) | **Nada** para leer. La `X-Api-Key` solo si vas a dar de alta o modificar unidades |
| Levantar el servicio en tu máquina | Las 3 claves de la base: `VEHICULOS_DB_URL`, `VEHICULOS_DB_USUARIO` y `VEHICULOS_DB_CLAVE` |
| Además, subir fotos | También `VEHICULOS_SUPABASE_URL` y `VEHICULOS_SUPABASE_CLAVE_SECRETA` |

### Qué es cada variable

| Variable | ¿Secreta? | Qué es | ¿Hay que pedirla? |
|---|---|---|---|
| `VEHICULOS_DB_URL` | No, pero no se publica | URL JDBC del *session pooler* de Supabase. Forma: `jdbc:postgresql://<host-del-pooler>:5432/postgres?sslmode=require` | **Sí**, el host exacto |
| `VEHICULOS_DB_USUARIO` | No, pero no se publica | Rol propio del servicio. Forma: `vehiculos_app.<project-ref>` | **Sí** |
| `VEHICULOS_DB_CLAVE` | **Sí** | Contraseña del rol `vehiculos_app` | **Sí** |
| `VEHICULOS_CLAVE_ESCRITURA` | **Sí** | Valor del header `X-Api-Key` que piden las escrituras. Mínimo 32 caracteres | Depende (ver abajo) |
| `VEHICULOS_FOTOS_MODO` | No | `deshabilitado` o `supabase` | No, lo elegís vos |
| `VEHICULOS_FOTOS_BUCKET` | No | Bucket de fotos: `vehiculos-fotos` | No |
| `VEHICULOS_SUPABASE_URL` | No | `https://<project-ref>.supabase.co` | Solo si `VEHICULOS_FOTOS_MODO=supabase` |
| `VEHICULOS_SUPABASE_CLAVE_SECRETA` | **Sí, la más sensible** | Clave `sb_secret_...` de Supabase. Tiene control total de Storage. Solo se usa en el backend | Solo si `VEHICULOS_FOTOS_MODO=supabase` |

**`VEHICULOS_CLAVE_ESCRITURA`:**

- Si levantás tu propia instancia, no hace falta pedirla: podés inventar una. Protege solo tu instancia. Para generarla:
  - con Bash o Git Bash: `openssl rand -base64 48`
  - con PowerShell: `[Convert]::ToBase64String((1..48 | % { [byte](Get-Random -Max 256) }))`
- Si vas a escribir contra una instancia que levantó otra persona, pedile la clave de esa instancia.

**Fotos:** si no las necesitás, usá `VEHICULOS_FOTOS_MODO=deshabilitado` y no pidas las dos claves de Storage. El servicio funciona igual. La única diferencia es que `PUT /vehiculos/{id}/foto` responde `503 ALMACENAMIENTO_NO_DISPONIBLE`.

---

## 2. Requisitos

| Herramienta | Para qué | Cómo verificarla |
|---|---|---|
| Git | Clonar el repo | `git --version` |
| Docker Desktop (o Docker Engine con Compose v2) | Correr con Docker y correr los tests | `docker compose version` |
| JDK 21 | Solo para correr con Maven o correr los tests | `java -version` |
| Maven 3.9 o superior | Solo para correr con Maven o correr los tests. El repo no trae `mvnw` | `mvn -v` |

Con el camino de Docker no necesitás Java ni Maven: el `Dockerfile` compila dentro del contenedor.

Para clonar el repo:

```bash
git clone https://github.com/naimgaunaa/VehiculosMicroservice.git
cd VehiculosMicroservice
```

Todos los comandos de este README se corren desde la raíz del repo.

---

## 3. Paso a paso con Docker (recomendado)

### Paso 1. Crear `infra/.env`

Copiá la plantilla. En Bash:

```bash
cp infra/.env.example infra/.env
```

En PowerShell:

```powershell
Copy-Item infra\.env.example infra\.env
```

`infra/.env` está en `.gitignore`, así que no se sube al repo. No lo compartas.

### Paso 2. Completar `infra/.env`

Abrilo con cualquier editor y completá los valores con lo que te pasaron. Ejemplo **sin fotos**, donde cada `<...>` es un valor a completar:

```dotenv
VEHICULOS_DB_URL=jdbc:postgresql://<host-del-pooler>:5432/postgres?sslmode=require
VEHICULOS_DB_USUARIO=vehiculos_app.<project-ref>
VEHICULOS_DB_CLAVE=<clave-de-la-base>
VEHICULOS_FOTOS_MODO=deshabilitado
VEHICULOS_SUPABASE_URL=
VEHICULOS_SUPABASE_CLAVE_SECRETA=
VEHICULOS_FOTOS_BUCKET=vehiculos-fotos
VEHICULOS_CLAVE_ESCRITURA=<clave-de-32-caracteres-o-mas>
```

Para subir fotos, cambiá estas tres líneas:

```dotenv
VEHICULOS_FOTOS_MODO=supabase
VEHICULOS_SUPABASE_URL=https://<project-ref>.supabase.co
VEHICULOS_SUPABASE_CLAVE_SECRETA=<sb_secret_...>
```

Antes de guardar, revisá tres cosas:

- **No quedó ningún `<` ni `>`.** El servicio los toma como un valor sin completar y no arranca.
- **No hay espacios alrededor del `=`.**
- **La URL usa el puerto 5432** (*session pooler*), no el 6543.

### Paso 3. Levantar el contenedor

Con Docker Desktop abierto, corré:

```bash
docker compose -f infra/docker-compose.yml up --build
```

Qué esperar:

- **La primera vez tarda unos minutos:** descarga las dependencias de Maven y compila. Las siguientes es mucho más rápido.
- **El compose lee `infra/.env` solo.** Si falta una variable obligatoria, corta con un mensaje como `Falta VEHICULOS_DB_URL ... Completala en infra/.env`.
- **Está listo cuando el log muestra `Started VehiculosApplication`.** Docker lo marca `healthy` cuando responde `/actuator/health`.
- **Para dejarlo corriendo en segundo plano**, agregá `-d`. Los logs se ven con `docker compose -f infra/docker-compose.yml logs -f`.

### Paso 4. Probarlo

Seguí la sección [Comprobar que funciona](#5-comprobar-que-funciona).

### Paso 5. Apagarlo

Si está en primer plano, apretá `Ctrl+C`. Si lo levantaste con `-d`, o para borrar el contenedor después del `Ctrl+C`:

```bash
docker compose -f infra/docker-compose.yml down
```

El contenedor siempre usa el perfil `docker` y **nunca** carga datos de prueba en la base compartida.

---

## 4. Paso a paso con Maven, sin Docker

Sirve para correr el servicio desde el IDE o la terminal. Necesitás JDK 21 y Maven.

### Paso 1. Crear y completar `infra/.env`

Igual que los pasos 1 y 2 de Docker.

### Paso 2. Cargar las variables en la terminal

Spring **no lee** archivos `.env` por su cuenta. Los scripts del repo cargan `infra/.env` en la terminal actual. Solo muestran los nombres de las variables, nunca los valores.

En Linux, macOS o Git Bash:

```bash
source scripts/cargar-env.sh
```

Tiene que ser con `source`. Si lo corrés como `./scripts/cargar-env.sh`, las variables se pierden al terminar el script (el propio script te avisa).

En Windows, con PowerShell:

```powershell
.\scripts\cargar-env.ps1
```

Si PowerShell bloquea el script por la política de ejecución, habilitalo solo para esa terminal y volvé a correrlo:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\cargar-env.ps1
```

Las variables quedan cargadas solo en esa terminal. Si abrís otra, hay que volver a cargarlas.

### Paso 3. Arrancar

En **la misma** terminal:

```bash
mvn -pl vehiculos-service spring-boot:run
```

Usa el perfil `local`, que es el que viene por defecto. Está listo cuando aparece `Started VehiculosApplication`. Para frenarlo, `Ctrl+C`.

**Desde el IDE (IntelliJ o VS Code):** la clase principal es `com.uade.crm.vehiculos.VehiculosApplication`. El IDE no ve las variables que cargaste en la terminal, así que hay que cargarlas en la configuración de ejecución. En IntelliJ: *Run/Debug Configurations › Environment variables*, donde podés pegar las líneas de `infra/.env`.

---

## 5. Comprobar que funciona

1. **Salud.** Abrí <http://localhost:8083/actuator/health>. Tiene que responder `"status":"UP"`.

2. **Swagger UI.** Abrí <http://localhost:8083/swagger-ui.html>. Desde ahí se prueban todos los endpoints. Para las escrituras, tocá **Authorize** y pegá la `X-Api-Key`.

3. **Listado de disponibles.** Devuelve las unidades disponibles de la base compartida:

   ```bash
   curl http://localhost:8083/vehiculos?disponible=true
   ```

4. **Precio de una unidad** (el Toyota Corolla de los datos de prueba):

   ```bash
   curl http://localhost:8083/vehiculos/a0000000-0000-4000-8000-000000000001/precio
   ```

5. **Escritura con clave.** Guardá el cuerpo en un archivo `alta.json`:

   ```json
   {
     "marca": "Toyota",
     "modelo": "Etios",
     "version": "XLS",
     "anio": 2020,
     "kilometraje": 60000,
     "precio": 9500000,
     "disponible": true,
     "caja": "MANUAL"
   }
   ```

   Después mandalo:

   ```bash
   curl -i -X POST http://localhost:8083/vehiculos \
     -H "Content-Type: application/json" \
     -H "X-Api-Key: $VEHICULOS_CLAVE_ESCRITURA" \
     --data-binary @alta.json
   ```

   - **Con la clave**, tiene que responder `201 Created` con el header `Location`. **Sin la clave**, responde `401 CLAVE_SERVICIO_INVALIDA`.
   - **Esto escribe en la base compartida.** Usá datos ficticios y avisale al equipo si cargás algo.
   - **En Windows**, usá `curl.exe` y pasá el JSON siempre desde un archivo (`--data-binary @alta.json`). Si lo escribís en la línea de comandos, PowerShell rompe las comillas.

### Probar con Postman

1. Importá la colección `docs/Servicio de Vehículos.postman_collection.json`, o generá una nueva importando `http://localhost:8083/v3/api-docs`.
2. Creá un *Environment* con estas variables:

   | Variable | Valor |
   |---|---|
   | `baseUrl` | `http://localhost:8083` |
   | `apiKey` | Tu `X-Api-Key` (marcala como *secret*) |
   | `idCorolla` | `a0000000-0000-4000-8000-000000000001` |
   | `idFicticio` | El id de alguna unidad del listado |

3. Para subir una foto, el Body del request es *form-data*, con un campo `archivo` de tipo *File*.

---

## 6. Correr los tests

Necesitás JDK 21, Maven y **Docker abierto**. No hace falta ninguna clave: los tests de integración levantan su propio PostgreSQL con Testcontainers y no tocan Supabase.

```bash
mvn -pl vehiculos-service -am verify
```

Tiene que terminar en `BUILD SUCCESS`. La primera vez descarga la imagen `postgres:17-alpine`.

También hay una suite opcional que corre los mismos tests contra Supabase, en el schema `vehiculos_test`, nunca en `vehiculos`. Necesita las claves de la base cargadas en la terminal (sección 4, paso 2):

```bash
mvn -pl vehiculos-service -am verify -Psupabase
```

---

## 7. API

Las rutas de la tabla son las internas del servicio. Detrás del gateway se exponen como `/api/vehiculos/...`.

| Método | Ruta | Clave | Qué hace |
|---|---|---|---|
| GET | `/vehiculos?disponible=true` | No | Lista de unidades. Sin el parámetro, devuelve todas |
| GET | `/vehiculos/{id}` | No | Detalle de una unidad. Responde 200 aunque no esté disponible |
| GET | `/vehiculos/{id}/precio` | No | Devuelve `{ "precio": 10000000.00, "disponible": true }` |
| GET | `/vehiculos/{id}/historial-precios` | No | Precios que rigieron, del más nuevo al más viejo |
| POST | `/vehiculos` | `X-Api-Key` | Alta de una unidad. Responde 201 con `Location` |
| PUT | `/vehiculos/{id}` | `X-Api-Key` | Cambia `precio`, `disponible` o los dos (al menos uno) |
| PUT | `/vehiculos/{id}/foto` | `X-Api-Key` | Carga la foto. Multipart con el campo `archivo`: JPEG, PNG o WebP de hasta 2 MB |

Formato de los campos:

- `caja` viaja como `AUTOMATICA` o `MANUAL`.
- `precio` es un número con 2 decimales.
- `version` y `fotoUrl` pueden llegar en `null`.

Los errores salen como `application/problem+json` y traen:

- `codigo`: por ejemplo `VEHICULO_NO_ENCONTRADO` (404), `VALIDACION` (422) o `CLAVE_SERVICIO_INVALIDA` (401).
- `correlationId`.
- En las validaciones, `errores: [{campo, mensaje}]`.

El servicio acepta el header `X-Correlation-Id` y lo devuelve en la respuesta. Si no viene, genera uno.

---

## 8. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `docker compose up` corta con `Falta VEHICULOS_... Completala en infra/.env` | La variable está vacía o falta en `infra/.env`. También puede estar definida con el mismo nombre en tu terminal, y esa gana sobre el archivo | Completala. Si en el archivo está bien, abrí una terminal nueva |
| Con Maven, el arranque falla con `'url' must start with "jdbc"` | No cargaste las variables en esa terminal, o corriste el script sin `source` | Repetí la sección 4, paso 2, en la misma terminal |
| El servicio no arranca y el error nombra una variable `VEHICULOS_*` (`es obligatoria`, `placeholder sin completar`, `al menos 32 caracteres`) | Falta la variable, quedó un `<...>` de la plantilla o la clave de escritura es demasiado corta | Corregí `infra/.env`, volvé a cargar las variables o a levantar el compose |
| Timeout o `Connection refused` contra la base | El proyecto gratuito de Supabase se pausa después de una semana sin uso | Pedile al responsable que lo reactive desde el dashboard. Revisá también que la URL use el puerto 5432 |
| `password authentication failed` | La clave o el usuario están mal copiados. El usuario lleva el sufijo `.<project-ref>` | Volvé a copiarlos, sin espacios ni comillas de más |
| `401 CLAVE_SERVICIO_INVALIDA` | Falta el header `X-Api-Key` o no coincide con la `VEHICULOS_CLAVE_ESCRITURA` de esa instancia | Revisá la clave. En Postman, fijate que el *Environment* esté seleccionado |
| `503 ALMACENAMIENTO_NO_DISPONIBLE` al subir una foto | `VEHICULOS_FOTOS_MODO=deshabilitado`, o Supabase Storage no responde | Pasá al modo `supabase` con sus dos claves |
| `400` o `415` al subir una foto desde Postman | Hay un header `Content-Type` puesto a mano, que pisa el que arma Postman para el multipart | Destildá el header `Content-Type` del request |
| `Port 8083 is already in use` o `address already in use` | Ya hay otra instancia corriendo, con Docker o con Maven | Frená la otra (`docker compose ... down` o `Ctrl+C`) |
| Los tests fallan con `Could not find a valid Docker environment` | Docker no está abierto | Abrí Docker Desktop y volvé a correr `verify` |
| Al arrancar: `Validate failed: Migrations have failed validation` | Tu rama y la base compartida no tienen las mismas migraciones | No arranques contra la base compartida una rama con migraciones nuevas. Avisale al responsable |

---

## 9. Reglas para no romper la base compartida

- **No agregues ni edites migraciones** (`vehiculos-service/src/main/resources/db/migration`). Solo el responsable migra la base compartida. Si tu rama trae una migración nueva, Flyway la aplica al arrancar y deja sin servicio a los demás.
- **No actives el perfil `seed` contra Supabase.** Los datos de prueba ya están cargados.
- **Usá solo datos ficticios** en las altas y en las fotos.
- **No subas nunca `infra/.env`** ni pegues claves en issues, PRs, chats grupales o capturas. Si se filtró alguna, avisá enseguida para rotarla.
