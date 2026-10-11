# Paso a paso: desarrollar `vehiculos` con Claude Code

Cada etapa tiene su prompt, algo para probar a mano y un commit. No pasás a la siguiente hasta que la actual compile y la pruebes. No le pidas todo junto: un prompt gigante da código que no revisás.

Las etapas 1 a 8 (incluida la 5.1) están hechas. Queda la etapa 9: la prueba de punta a punta sobre el contenedor (la hacés vos, con Postman), la auditoría final y el README. La sección 5 explica qué hacer cuando un paso falla y cómo pasarle la falla a Claude Code.

## 0. Antes de empezar

Tenés que tener instalados Java 21, Maven 3.9, Docker Desktop (abierto, lo usan los tests y el contenedor), Git, Node 18+ y Postman. Verificá con `java -version`, `mvn -v`, `docker version` y `git --version`. Claude Code se instala con `npm install -g @anthropic-ai/claude-code`.

De Supabase tenés que tener a mano: la contraseña del rol `vehiculos_app`, el host del session pooler, el project-ref, la clave `sb_secret_...` y el bucket `vehiculos-fotos` ya creado.

## 1. Repo y carpetas (hecho)

`arquitectura.md` va en `docs/` y `CLAUDE.md` en la raíz. `infra/.env.example` está commiteado con placeholders y `infra/.env` tiene los valores reales y no se commitea nunca. `VEHICULOS_CLAVE_ESCRITURA` tiene 32 caracteres o más (`openssl rand -base64 32`). El repo del servicio `clientes` queda fuera de este repo (`../ClientMicroservice-dev`): Claude Code lo usa solo como referencia de estilo.

## 2. Cómo se trabaja cada etapa

1. Rama desde `dev` actualizado: `git switch dev && git pull origin dev && git switch -c feature/vehiculos-<tema>`.
2. Abrí `claude` en la raíz del repo, con Opus 5.5 en esfuerzo alto (`/model`), y pegá el prompt. Los prompts piden trabajar en modo plan: Claude Code te muestra el plan y las decisiones abiertas, y no escribe hasta que le des el OK. Contestale las decisiones con tu criterio, no con un "dale" automático.
3. Leé el diff (`git diff`). Lo que no entiendas, preguntalo ahí: en la materia te van a preguntar por ese código.
4. Corré `mvn -pl vehiculos-service -am verify` y la prueba manual de la etapa.
5. Cierre de etapa: `CLAUDE.md` le indica a Claude Code que, cuando todo compila y los tests pasan, te proponga qué pendientes y lecciones agregar a `docs/arquitectura.md` (sección 13) y a `CLAUDE.md`, y que marque los pendientes ya resueltos. Revisá la lista y aprobá solo lo que sirva. Los documentos no se tocan a mitad de etapa.
6. Commit, pull request a `dev` y `/clear` antes de la etapa siguiente. `CLAUDE.md` se vuelve a leer solo.

**Conexión a Supabase desde Claude Code.** Para que Claude Code no necesite leer `infra/.env` (todo lo que lee pasa por la conversación), cargá las variables en la terminal **antes** de abrir `claude`: `source scripts/cargar-env.sh` (o `scripts/cargar-env.ps1`) y después `claude` en esa misma terminal. Los comandos que corra heredan esas variables. `verify` corre en Testcontainers y no necesita credenciales; `-Psupabase` corre la misma batería contra el schema `vehiculos_test`, nunca contra `vehiculos`. Al terminar el TP, rotá o descartá las credenciales.

## 3. Etapas hechas

- **Etapa 1, esqueleto:** POM padre con solo `vehiculos-service`, perfil local, scripts de carga de variables.
- **Etapa 2, transversal:** `ApiExceptionHandler`, `CodigoError`, `CorrelationIdFilter`, logback, OpenAPI, Actuator, `VehiculosProperties`.
- **Etapa 3, dominio:** `Vehiculo` con Builder e invariantes, `Caja`, `PrecioHistorico`, excepciones.
- **Etapa 4, persistencia y seed:** V1, entidades, repositorios, adaptador JPA, tests con Testcontainers, `R__seed_vehiculos.sql` con 10 unidades ficticias y perfil `seed`.
- **Etapa 5, lectura:** los cuatro `GET`, `VEHICULO_NO_ENCONTRADO`, seed cargado en Supabase.
- **Etapa 5.1, suite contra Supabase:** `-Psupabase` en el schema `vehiculos_test`, con la guarda `GuardaSchemaDePrueba`.
- **Etapa 6, escrituras:** `POST` y `PUT` con `X-Api-Key`, historial de precios en la misma transacción.
- **Etapa 7, fotos:** `PUT /vehiculos/{id}/foto`, Strategy `FotoStorage` con Supabase Storage y modo `deshabilitado`.
- **Etapa 8, Docker:** `Dockerfile`, `.dockerignore` de lista blanca, `infra/docker-compose.yml` y perfil `docker`.

Los prompts de las etapas 5 a 8 quedaron en la versión anterior de esta guía.

## 4. Etapa 9: prueba de punta a punta sobre Docker

**Qué tiene que quedar demostrado.** No es una prueba de todos los casos de uso (los bordes ya los cubren los 346 tests). Es la prueba de que el sistema armado (imagen, compose, variables, red, Supabase y Storage) funciona junto. La etapa está aprobada cuando se cumplen tres cosas:

1. El servicio se levanta solo con `docker compose`, sin Java ni Maven corriendo en tu máquina, y queda `healthy`.
2. Cada endpoint responde lo que dice la sección 5 de `docs/arquitectura.md`, y lo que escribe queda en Supabase. Eso lo comprobás en el SQL Editor y en Storage, no solo mirando la respuesta.
3. Con el contenedor abajo, ningún request de Postman obtiene respuesta. Al volver a levantarlo, los datos siguen ahí.

Rama: `feature/vehiculos-e2e` desde `dev` actualizado. Si un caso falla y hay que tocar código, seguí la sección 5 y no lo arregles en esta rama.

### 9.1 Preparación

**Avisá al equipo.** Vas a escribir en la base compartida y a limpiar datos de prueba de las etapas anteriores.

**Supabase activo.** Entrá al dashboard. Si el proyecto dice *Paused*, restauralo y esperá a que quede activo.

**Una terminal limpia para el compose.** Abrí una terminal nueva y **no** cargues `cargar-env.sh`. Las variables del shell ganan sobre `infra/.env` (lección de la etapa 8): si están cargadas, el contenedor usa las de la terminal y no estás probando el archivo que va a usar el equipo.

```bash
env | grep -c '^VEHICULOS_'
```

Tiene que dar `0`. En PowerShell: `(Get-ChildItem Env: | Where-Object Name -like 'VEHICULOS_*').Count`.

**Puerto 8083 libre.** Si quedó corriendo un `spring-boot:run`, un run de IntelliJ o un contenedor viejo, Postman le pegaría a eso y la prueba no vale.

```bash
docker ps --filter "publish=8083"
lsof -i :8083
```

Los dos tienen que salir vacíos (el primero, solo con la fila de títulos). En Windows, en vez de `lsof`: `netstat -ano | findstr :8083`. Si hay algo, cortalo antes de seguir.

**Línea de base en Supabase.** Las etapas 6 a 8 dejaron datos de prueba en el schema `vehiculos` (sección 13, pendientes de la etapa 9). Primero mirá qué hay fuera del seed, en el SQL Editor:

```sql
SELECT id, marca, modelo, precio, disponible, foto_url
FROM vehiculos.vehiculo
WHERE id::text NOT LIKE 'a0000000-%' OR foto_url IS NOT NULL;

SELECT vehiculo_id, precio, vigente_desde
FROM vehiculos.precio_historial
WHERE id::text NOT LIKE 'b0000000-%';
```

Lo esperable: las dos unidades "Ficticia" (`93a56a83-…` y `ec1b5aae-…`), el Fiesta con foto y una segunda fila de historial del Corolla. Si aparece algo más, es de otra persona: no lo borres sin preguntar. Si es solo eso, limpiá:

```sql
BEGIN;
DELETE FROM vehiculos.precio_historial
WHERE vehiculo_id IN ('93a56a83-07fd-452e-be77-378d31d4e948', 'ec1b5aae-2202-4ea6-8c4a-be64b8d3796a');
DELETE FROM vehiculos.vehiculo
WHERE id IN ('93a56a83-07fd-452e-be77-378d31d4e948', 'ec1b5aae-2202-4ea6-8c4a-be64b8d3796a');
DELETE FROM vehiculos.precio_historial
WHERE vehiculo_id = 'a0000000-0000-4000-8000-000000000001' AND id::text NOT LIKE 'b0000000-%';
UPDATE vehiculos.vehiculo SET precio = 24500000.00 WHERE id = 'a0000000-0000-4000-8000-000000000001';
UPDATE vehiculos.vehiculo SET foto_url = NULL WHERE id = 'a0000000-0000-4000-8000-000000000002';
UPDATE vehiculos.vehiculo SET actualizado_en = '2026-09-05 10:00:00+00' WHERE id = 'a0000000-0000-4000-8000-000000000005';
COMMIT;
```

En Storage, borrá del bucket `vehiculos-fotos` el objeto `ec1b5aae-2202-4ea6-8c4a-be64b8d3796a.png` y cualquier otro que haya quedado de pruebas.

**Consulta de control.** La vas a repetir al final:

```sql
SELECT count(*) AS total,
       count(*) FILTER (WHERE disponible) AS disponibles,
       count(*) FILTER (WHERE NOT disponible) AS no_disponibles,
       count(foto_url) AS con_foto
FROM vehiculos.vehiculo;

SELECT count(*) AS filas_historial FROM vehiculos.precio_historial;

SELECT installed_rank, version, description, success
FROM vehiculos.flyway_schema_history
ORDER BY installed_rank;
```

Esperado: `10`, `8`, `2`, `0`; `10` filas de historial; en Flyway, la `V1` (`vehiculo`) y la repetible `seed vehiculos`, las dos con `success` en `true`, y ninguna fila en `false`. Esto cierra el pendiente "revisar que `flyway_schema_history` no tenga filas fallidas".

**Archivos de prueba.** Prepará una carpeta fuera del repo (por ejemplo `~/pruebas-vehiculos/`) con:

- `auto.png`: cualquier PNG de menos de 2 MB.
- `auto.jpg`: cualquier JPG de menos de 2 MB.
- `documento.pdf`: cualquier PDF.
- `falso.png`: el PDF copiado con otro nombre (`cp documento.pdf falso.png`).
- `grande.jpg`: más de 2 MB. Una foto del celular suele pesar eso; si no tenés, `head -c 2500000 /dev/urandom > grande.jpg` sirve igual, porque falla por tamaño o por tipo y da el mismo 422.

Solo imágenes genéricas: nada con datos personales.

### 9.2 Levantar el servicio desde Docker

Todo desde la raíz del repo, en la terminal limpia. En PowerShell usá `curl.exe` en vez de `curl`.

1. **Validá el compose sin imprimir secretos:**

   ```bash
   docker compose -f infra/docker-compose.yml config --quiet && echo OK
   ```

   Usá siempre `--quiet`. `config` sin ese flag imprime el compose con las variables ya reemplazadas, o sea, con las claves a la vista. Compose lee `infra/.env` solo porque está en la misma carpeta que el `docker-compose.yml`.

2. **Construí y levantá en segundo plano:**

   ```bash
   docker compose -f infra/docker-compose.yml up --build -d
   ```

   `--build` asegura que la imagen tenga el código actual de `dev`. Sin él, compose reutiliza la imagen de la etapa 8.

3. **Esperá `healthy`:**

   ```bash
   docker compose -f infra/docker-compose.yml ps
   ```

   La columna de estado pasa de `health: starting` a `healthy` en 10 a 20 segundos, y en puertos tiene que figurar `0.0.0.0:8083->8083/tcp`. Si queda `Restarting` o `Exited`, andá a la sección 5.

4. **Revisá el arranque:**

   ```bash
   docker compose -f infra/docker-compose.yml logs vehiculos
   ```

   Tenés que ver a Flyway validando 2 migraciones y diciendo que el schema `vehiculos` está al día sin aplicar nada, la línea `Started ... in X seconds`, y como únicos WARN los dos de springdoc. No tiene que aparecer `No migrations found` ni ninguna clave.

5. **Health y usuario:**

   ```bash
   curl -s http://localhost:8083/actuator/health
   docker compose -f infra/docker-compose.yml exec vehiculos whoami
   ```

   El primero devuelve `{"status":"UP"}`. El segundo devuelve `vehiculos`, nunca `root`.

### 9.3 Postman

1. **Importá la API** con el contenedor arriba: botón *Import*, pegá `http://localhost:8083/v3/api-docs`. Postman arma una colección con los 7 endpoints. Si la colección trae su propia variable `baseUrl`, no la toques: la del entorno gana.

2. **Creá el entorno `vehiculos-docker`** y seleccionalo arriba a la derecha:

   | Variable | Tipo | Valor |
   |---|---|---|
   | `baseUrl` | default | `http://localhost:8083` |
   | `apiKey` | secret | el valor de `VEHICULOS_CLAVE_ESCRITURA` de `infra/.env`, copiado a mano |
   | `idCorolla` | default | `a0000000-0000-4000-8000-000000000001` |
   | `idSandero` | default | `a0000000-0000-4000-8000-000000000005` |
   | `idInexistente` | default | `9e8d7c6b-5a4f-4e3d-2c1b-0a9f8e7d6c5b` |
   | `vehiculoId` | default | vacío, lo llena el alta |

   La clave va solo en *Current value*, con *Initial value* vacío. *Initial value* se sincroniza con tu cuenta de Postman y con quien comparta el workspace; *Current value* queda en tu máquina.

3. **Autenticación de la colección.** En la colección, pestaña *Authorization*: tipo *API Key*, Key `X-Api-Key`, Value `{{apiKey}}`, Add to *Header*. Cada request queda en *Inherit auth from parent*. Para los casos sin clave, el request va con *No Auth*. Para el de clave incorrecta, duplicá el request y en su *Authorization* poné el mismo tipo con Value `clave-incorrecta`. Mantené los casos negativos como requests duplicados con nombre propio (por ejemplo "B1 alta sin clave"), así no tocás los que funcionan.

4. **Guardá el id del alta.** En el request `POST /vehiculos`, pestaña *Scripts* > *Post-response* (en versiones viejas, *Tests*):

   ```js
   if (pm.response.code === 201) {
     pm.environment.set("vehiculoId", pm.response.json().id);
   }
   ```

5. **Anotá el `correlationId`** de toda respuesta que no sea la esperada. Con ese valor encontrás la línea exacta en los logs (sección 5).

### 9.4 Casos

Recorrelos en orden: cada bloque deja datos que usa el siguiente. En cada caso anotá OK o el resultado real. Las rutas van con `{{baseUrl}}` adelante.

**Bloque A: lecturas sobre el seed.** Solo leen, no hay nada que verificar en Supabase.

| # | Request | Esperado |
|---|---|---|
| A1 | `GET /vehiculos` | `200`, 10 unidades, Citroën C3 primero y Toyota Corolla último. Ningún objeto trae `creadoEn` ni `actualizadoEn`; `fotoUrl` en `null` en todas; `caja` en `AUTOMATICA` o `MANUAL` |
| A2 | `GET /vehiculos?disponible=true` | `200`, 8 unidades |
| A3 | `GET /vehiculos?disponible=false` | `200`, 2 unidades: Renault Sandero y Honda Civic |
| A4 | `GET /vehiculos/{{idCorolla}}` | `200`, `precio` 24500000 como número (con o sin `.00`, según lo que se decidió en la etapa 5; nunca como texto) |
| A5 | `GET /vehiculos/{{idSandero}}` | `200` con `disponible` en `false`, no `404` |
| A6 | `GET /vehiculos/{{idCorolla}}/precio` y `GET /vehiculos/{{idSandero}}/precio` | `200`, solo `precio` y `disponible`; el Sandero con `disponible` en `false` |
| A7 | `GET /vehiculos/{{idCorolla}}/historial-precios` | `200`, una fila con el mismo precio y `vigenteDesde` de septiembre de 2026 |
| A8 | `GET /vehiculos/{{idInexistente}}`, y lo mismo con `/precio` y `/historial-precios` | `404`, `Content-Type: application/problem+json`, `codigo` `VEHICULO_NO_ENCONTRADO` y `correlationId` |
| A9 | `GET /vehiculos/no-es-un-uuid` | `400`, `SOLICITUD_INVALIDA` |
| A10 | `GET /ruta-inexistente` con el header `X-Correlation-Id: e2e-001` | `404`, `SOLICITUD_INVALIDA`, `correlationId` `e2e-001` en el cuerpo y el header de respuesta `X-Correlation-Id` con el mismo valor |
| A11 | En el navegador, `http://localhost:8083/swagger-ui.html` | Redirige a `/swagger-ui/index.html`; el `POST` y los dos `PUT` muestran el candado |

A10 cierra el pendiente de la etapa 2 que quedaba sobre el contenedor.

**Bloque B: seguridad de las escrituras.** Ninguno de estos debe escribir nada. Para B1 y B2 usá el cuerpo base del bloque C.

| # | Request | Esperado |
|---|---|---|
| B1 | `POST /vehiculos`, *No Auth*, cuerpo válido | `401`, `CLAVE_SERVICIO_INVALIDA` |
| B2 | `POST /vehiculos` con `X-Api-Key: clave-incorrecta` | `401`; la respuesta no repite la clave enviada ni la configurada |
| B3 | `POST /vehiculos`, *No Auth*, cuerpo `{}` | `401` y no `422`: la clave se evalúa antes que el cuerpo |
| B4 | `PUT /vehiculos/{{idCorolla}}`, *No Auth*, `{"precio": 1}` | `401` |
| B5 | `PUT /vehiculos/{{idCorolla}}/foto`, *No Auth*, con `auto.png` | `401` |

En Supabase: la consulta de control sigue en `10`, `8`, `2`, `0`, y el Corolla sigue con precio `24500000.00`.

**Bloque C: alta.** Cuerpo base:

```json
{
  "marca": "Ficticia",
  "modelo": "Prueba E2E",
  "version": "Docker",
  "anio": 2024,
  "kilometraje": 1000,
  "precio": 15000000,
  "caja": "MANUAL"
}
```

| # | Request | Esperado |
|---|---|---|
| C1 | `POST /vehiculos` con el cuerpo base | `201`; header `Location` que termina en `/vehiculos/{id}`; cuerpo con `id`, `disponible` en `true` (no se mandó) y `fotoUrl` en `null`. El script guarda `vehiculoId` |
| C2 | `GET /vehiculos` | `200`, 11 unidades, con la Prueba E2E primera (usa el reloj real y las sembradas son de septiembre) |
| C3 | `GET /vehiculos/{{vehiculoId}}/historial-precios` | `200`, una fila con 15000000 |
| C4 | `POST` sin `caja` | `422`, `VALIDACION`, `errores` con el campo `caja` |
| C5 | `POST` con `"caja": "Automática"` | `422`, con los valores aceptados en el mensaje |
| C6 | `POST` con `"anio": 2028` | `422`, `errores` con el campo `anio` |
| C7 | `POST` con `"precio": 0` | `422`, `errores` con el campo `precio` |
| C8 | `POST` con `"marca": "   "` | `422`, `errores` con el campo `marca` (los textos se recortan antes de validar) |
| C9 | `POST` con un campo de más, `"color": "rojo"` | `422`, `errores` con `campo` `color` y `mensaje` `campo no admitido` |
| C10 | `POST` con JSON roto: `{"marca": "Ficticia",` | `400`, `JSON_INVALIDO` |

En Supabase, después de C1 (la llamamos **consulta E2E** y la vas a repetir):

```sql
SELECT v.id, v.precio, v.disponible, v.foto_url, v.creado_en, v.actualizado_en,
       (SELECT count(*) FROM vehiculos.precio_historial h WHERE h.vehiculo_id = v.id) AS filas_historial
FROM vehiculos.vehiculo v
WHERE v.modelo = 'Prueba E2E';
```

Esperado: una fila con precio `15000000.00`, `disponible` en `true`, `foto_url` en `NULL`, `creado_en` igual a `actualizado_en` y `filas_historial` en `1`. Después de C4 a C10, `SELECT count(*) FROM vehiculos.vehiculo` sigue en `11`: un `422` o un `400` no escribe nada.

**Bloque D: actualización.**

| # | Request | Esperado |
|---|---|---|
| D1 | `PUT /vehiculos/{{vehiculoId}}` con `{"precio": 15500000}` | `200`, `precio` 15500000 |
| D2 | `GET /vehiculos/{{vehiculoId}}/historial-precios` | `200`, 2 filas: primero 15500000 y después 15000000 |
| D3 | `PUT` con `{"precio": 15500000.00}` (el mismo precio con decimales) | `200` sin cambios |
| D4 | `PUT` con `{"disponible": false}` | `200`, `disponible` en `false` |
| D5 | `GET /vehiculos?disponible=false` y `GET /vehiculos/{{vehiculoId}}/precio` | 3 unidades en el listado; `/precio` responde `200` con `disponible` en `false` |
| D6 | `PUT` con `{"disponible": true}` | `200` |
| D7 | `PUT` con `{}` | `422`, `errores` con el campo `precioODisponible` |
| D8 | `PUT` con `{"precio": 0}` | `422` |
| D9 | `PUT` con `{"precio": 100, "marca": "X"}` | `422`, `errores` con `campo` `marca` y `mensaje` `campo no admitido` |
| D10 | `PUT /vehiculos/{{idInexistente}}` con `{"precio": 100}` | `404`, `VEHICULO_NO_ENCONTRADO` |

En Supabase, con la consulta E2E:

- Después de D1: precio `15500000.00`, `filas_historial` en `2` y `actualizado_en` posterior a `creado_en`. Anotá el valor de `actualizado_en`.
- Después de D3: `filas_historial` sigue en `2` y `actualizado_en` es el mismo que anotaste. Un `PUT` que no cambia nada no guarda.
- Después de D4 y D6: `filas_historial` sigue en `2`. Cambiar la disponibilidad no escribe historial.
- Después de D9: el precio sigue en `15500000.00`. Un cuerpo rechazado no se aplica a medias.

**Bloque E: fotos.** Body *form-data*, key `archivo` con tipo *File* (el desplegable a la derecha del nombre de la key). No agregues el header `Content-Type` a mano: Postman arma el boundary del multipart.

| # | Request | Esperado |
|---|---|---|
| E1 | `PUT /vehiculos/{{vehiculoId}}/foto` con `auto.png` | `200`, `fotoUrl` igual a `https://<ref>.supabase.co/storage/v1/object/public/vehiculos-fotos/{vehiculoId}.png`; la URL abre la imagen en el navegador |
| E2 | El mismo request con `auto.jpg` | `200`, `fotoUrl` termina en `.jpg` |
| E3 | Con `documento.pdf` | `422`, `FOTO_INVALIDA` |
| E4 | Con `falso.png` | `422`, `FOTO_INVALIDA`: el tipo se detecta por los bytes, no por el nombre |
| E5 | Con `grande.jpg` | `422`, `FOTO_INVALIDA`, nunca `413` ni `500` |
| E6 | Key con otro nombre (`foto` en vez de `archivo`) | `400` |
| E7 | Body *raw* JSON en vez de *form-data* | `415` |
| E8 | `PUT /vehiculos/{{idInexistente}}/foto` con `auto.png` | `404` |

En Supabase:

- Después de E1: en Storage, el bucket tiene `{vehiculoId}.png`; en la consulta E2E, `foto_url` tiene esa URL y `filas_historial` sigue en `2`.
- Después de E2: en Storage está el `.jpg` y ya no el `.png`. No lo compruebes abriendo la URL vieja: la CDN puede seguir sirviéndola un rato (lección de la etapa 7). Mirá el bucket en el dashboard.
- Después de E3 a E8: `foto_url` sigue apuntando al `.jpg` y en el bucket hay un solo objeto de esa unidad.

**Bloque F (opcional): modo deshabilitado.** Prueba la otra estrategia de `FotoStorage` sin editar el `.env`, porque la variable del shell gana sobre el archivo:

```bash
VEHICULOS_FOTOS_MODO=deshabilitado docker compose -f infra/docker-compose.yml up -d
```

En PowerShell: `$env:VEHICULOS_FOTOS_MODO="deshabilitado"`, el mismo `up -d`, y después `Remove-Item Env:VEHICULOS_FOTOS_MODO`. Compose recrea el contenedor. Esperá `healthy` y probá: `PUT /vehiculos/{{vehiculoId}}/foto` con `auto.png` da `503` `ALMACENAMIENTO_NO_DISPONIBLE`, y `GET /vehiculos/{{vehiculoId}}` sigue dando `200` con la `fotoUrl` del `.jpg`. Para volver, `docker compose -f infra/docker-compose.yml up -d` sin la variable, y confirmá con `env | grep -c '^VEHICULOS_'` que la terminal quedó en `0`.

**Bloque G: contenedor abajo.**

1. `docker compose -f infra/docker-compose.yml down`. `docker ps` ya no muestra el contenedor.
2. En Postman, repetí A1, C1 y E1. Ninguno obtiene status HTTP: Postman muestra *Could not send request* con `ECONNREFUSED 127.0.0.1:8083`. Si alguno responde, hay otro proceso en el 8083 (sección 5) y hay que repetir toda la prueba, porque no sabés a qué le pegaste.
3. En Supabase, la consulta E2E sigue devolviendo la unidad con precio `15500000.00` y la foto `.jpg`. La base no depende del contenedor, pero la API de `vehiculos` no existe sin él: Supabase no expone el schema `vehiculos` por su Data API.
4. `docker compose -f infra/docker-compose.yml up -d`, sin `--build` porque la imagen ya está. Esperá `healthy` y repetí `GET /vehiculos/{{vehiculoId}}`: mismos datos. La persistencia está en Supabase, no en el contenedor.

**Bloque H: logs y secretos.**

```bash
docker compose -f infra/docker-compose.yml logs --no-color vehiculos > /tmp/vehiculos-e2e.log
grep -c "sb_secret" /tmp/vehiculos-e2e.log
grep -c "No migrations found" /tmp/vehiculos-e2e.log
```

Los dos tienen que dar `0`. Para buscar los valores exactos de las claves sin escribirlos, abrí **otra** terminal con las variables cargadas (no uses la del compose):

```bash
source scripts/cargar-env.sh
for v in VEHICULOS_CLAVE_ESCRITURA VEHICULOS_DB_CLAVE VEHICULOS_SUPABASE_CLAVE_SECRETA; do
  printf '%s: ' "$v"; grep -cF -- "${!v}" /tmp/vehiculos-e2e.log
done
rm /tmp/vehiculos-e2e.log
```

Los tres tienen que dar `0`. Después, en la terminal limpia, comprobá la primera barrera contra una clave faltante sin bajar el servicio:

```bash
VEHICULOS_CLAVE_ESCRITURA= docker compose -f infra/docker-compose.yml config --quiet
```

Tiene que fallar con el mensaje del `${VAR:?}`. Que la app no arranca sin la clave ya se probó en la etapa 8.

**Bloque I: cierre de la prueba.**

1. Decidí si la unidad Prueba E2E queda como dato de ejemplo para el equipo o se borra. Para borrarla:

   ```sql
   BEGIN;
   DELETE FROM vehiculos.precio_historial
   WHERE vehiculo_id IN (SELECT id FROM vehiculos.vehiculo WHERE modelo = 'Prueba E2E');
   DELETE FROM vehiculos.vehiculo WHERE modelo = 'Prueba E2E';
   COMMIT;
   ```

   y borrá `{vehiculoId}.jpg` del bucket. Repetí la consulta de control: `10`, `8`, `2`, `0` y `10`.
2. `docker compose -f infra/docker-compose.yml down`.
3. Si querés que el equipo use tu colección: exportala (solo la colección, nunca el entorno) a `docs/postman/vehiculos.postman_collection.json`. Antes de commitearla, en una terminal con las variables cargadas, `grep -cF -- "$VEHICULOS_CLAVE_ESCRITURA" docs/postman/vehiculos.postman_collection.json` tiene que dar `0`.
4. Si trabajás en Windows, probá `scripts/cargar-env.ps1` en PowerShell real (pendiente de la etapa 1).
5. Pasale los resultados a Claude Code para cerrar la documentación. Sesión nueva (`/clear`), Opus 5.5 en esfuerzo alto:

```text
Cierre de la etapa 9 (prueba de punta a punta sobre el contenedor). No toques código.

Resultados de la prueba manual con Postman contra Supabase, hecha el <fecha>, con la imagen construida desde dev (<hash del último commit>):
<pegá la lista de casos A1 a I con OK o el resultado real; sin claves ni valores de variables>

Con eso:
1. En docs/arquitectura.md §13, marcá con **[Resuelto en la etapa 9]** los pendientes de "Etapa 9" que esta prueba cubre, con la regla de CLAUDE.md: solo lo que tenga respaldo en estos resultados. Lo que no se probó queda sin marcar y me lo listás.
2. Si algún resultado contradice las secciones 3.3, 5, 7 o 12, no corrijas nada: decime qué difiere y si conviene cambiar el código o el documento.
3. Proponé los cambios y esperá mi OK antes de editar.
```

### 9.5 Auditoría final con Claude Code

Sesión nueva (`/clear`), Opus 5.5 en esfuerzo alto, solo lectura:

```text
Auditoría final de vehiculos. No modifiques ningún archivo: reportá hallazgos ordenados por gravedad, con archivo y línea, y qué cambiarías. Si necesitás confirmar algo contra la base real, las variables VEHICULOS_* están cargadas en esta terminal: usalas sin imprimir sus valores y sin escribir en la base.

Leé CLAUDE.md y docs/arquitectura.md completo, y recorré todo el código, los tests, el Dockerfile, el .dockerignore, el compose y los scripts.

Chequeá:
1. Cumplimiento de la especificación: cada endpoint, código de error, invariante y regla de las secciones 3 a 7 y 9. Listá lo que el documento pide y el código no hace, y lo que el código hace y el documento no dice.
2. Seguridad: secretos en el repo y en el historial de git (git log -p y git grep sobre sb_secret, claves y contraseñas), .gitignore, .dockerignore, que ningún secreto aparezca en logs, toString o respuestas de error, validación del archivo subido, comparación en tiempo constante de X-Api-Key, y que ninguna lectura exponga creadoEn o actualizadoEn.
3. Transacciones y consistencia: historial de precios atómico, actualizado_en, qué pasa si falla Storage a mitad de la subida, y concurrencia entre dos PUT (el documento acepta que gana el último: confirmá que el historial no queda incoherente).
4. Arquitectura y convenciones: dependencias entre capas (el dominio no importa Spring Data ni clientes HTTP), visibilidad de paquete de entidades y adaptador, package-info.java en cada paquete, DTOs record con de(...), sin Lombok ni MapStruct, patrones Repository, Builder y Strategy visibles y bien aplicados.
5. Tests: huecos de cobertura en reglas del documento, tests que dependen del orden o del reloj, y confirmación de que ninguno llama a Supabase Storage.
6. Estado de la documentación: cada pendiente de la sección 13 y de la sección 15 marcado como resuelto, vigente o desactualizado, con evidencia. Incluí el comentario desactualizado de application.yml que anotó la etapa 8.

Si algo se desvía del documento, decime cuál de los dos conviene corregir. Lo que sea mejora y no defecto va a docs/mejoras.md. Cerrá con una lista corta de lo que bloquea la entrega y lo que puede esperar.
```

De los hallazgos tomá solo los de gravedad alta y media, corregilos en una rama aparte (`feature/vehiculos-auditoria`) y dejá el resto anotado. Antes de corregir un defecto, que Claude Code escriba el test que lo demuestra. Si la corrección toca código, repetí sobre el contenedor el bloque de 9.4 que corresponda.

### 9.6 README para el equipo

```text
Escribí README.md en la raíz de vehiculos para alguien del equipo que nunca lo vio. Tiene que alcanzar para levantar y probar el servicio con Docker sin leer docs/arquitectura.md.

Incluí: qué es el servicio en tres líneas; requisitos (Docker para usarlo, y Java 21 y Maven solo para desarrollar); cómo crear infra/.env a partir de infra/.env.example y cómo recibir las credenciales (por un canal seguro, nunca por el repo); cómo levantar con docker compose y cómo saber que quedó healthy; que hay que usar una terminal sin las VEHICULOS_* cargadas, porque las del shell ganan sobre infra/.env; que docker compose config se corre siempre con --quiet; cómo ver Swagger UI; cómo importar la colección en Postman y configurar X-Api-Key como variable secreta solo en Current value; la tabla de endpoints; cómo correr los tests y qué necesitan; y las advertencias de la base compartida (avisar antes de cargar el seed, migraciones solo del responsable, el proyecto de Supabase se pausa por inactividad).

No pongas valores reales ni inventes comandos: probá cada uno antes de escribirlo, y si alguno no pudiste ejecutarlo, marcalo.
```

Cuando todo esté revisado: pull request a `dev`. El paso de `dev` a `main` lo hacés al final de todo el TP.

## 5. Si algo falla

### 5.1 Ubicá la capa antes de tocar nada

Casi toda falla está en una de tres capas, y cada una se mira distinto:

- **El contenedor no levanta o no queda `healthy`:** compose, variables o conexión a Supabase. Se mira con `docker compose -f infra/docker-compose.yml ps` y `docker compose -f infra/docker-compose.yml logs --tail 100 vehiculos`.
- **El contenedor está `healthy` pero un caso da otro resultado:** la aplicación, o el request está mal armado en Postman. Se mira con el `correlationId` de la respuesta: `docker compose -f infra/docker-compose.yml logs vehiculos | grep <correlationId>`.
- **La respuesta está bien pero Supabase no muestra el cambio:** casi siempre estás mirando otro schema u otra unidad, o le pegaste a otro proceso. Revisá el `id` y que la consulta diga `vehiculos.`.

### 5.2 Síntomas frecuentes

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| `permission denied while trying to connect to the Docker daemon socket` | En Linux, tu usuario no está en el grupo `docker` | `sudo usermod -aG docker $USER` y volvé a iniciar sesión. Claude Code tampoco puede usar Docker hasta que `docker ps` funcione sin `sudo` |
| Docker Desktop se cierra con `qemu: process terminated unexpectedly: signal: aborted` (Linux) | La VM de Docker Desktop se quedó sin memoria o sin disco, casi siempre durante el build de Maven | *Quit* (no *Reset to factory defaults*), revisá `free -h` y `df -h`, reiniciá con `systemctl --user restart docker-desktop`, separá `build` de `up -d`. Si se repite, usá Docker Engine nativo, que en Linux no levanta VM |
| `required variable VEHICULOS_X is missing a value` | Falta en `infra/.env`, está vacía o la vació una variable del shell | Completala, una variable por línea, en una terminal sin las `VEHICULOS_*` cargadas |
| `Bind for 0.0.0.0:8083 failed: port is already allocated` | Quedó un `spring-boot:run`, un run del IDE u otro contenedor | Cortalo; buscalo con `docker ps` y `lsof -i :8083` |
| Contenedor en `Restarting` o `Exited (1)` | El arranque falla y `on-failure:3` lo reintenta 3 veces | Leé los logs. `UnknownHostException`: host del pooler mal o con el placeholder. `tenant/user ... not found`: usuario sin `.<project-ref>` o con otro ref. `password authentication failed`: clave del rol. Timeout sin más: proyecto pausado. "al menos 32 caracteres" o "placeholder sin completar": `VEHICULOS_CLAVE_ESCRITURA`. `Invalid sslmode value`: dos variables pegadas en una línea |
| Arrancó bien y después pasó a `unhealthy` | La base dejó de responder (pausa o red) | Reactivá el proyecto y hacé `docker compose -f infra/docker-compose.yml up -d`. Docker no reinicia solo un contenedor `unhealthy` |
| Flyway: `Validate failed`, `checksum mismatch` o `applied migration not resolved locally` | Alguien editó una migración o el seed, o aplicó una `V2` desde otra rama | No corras `repair` ni borres filas de `flyway_schema_history` sin entender qué pasó. Mirá esa tabla, avisá al equipo y pasale el log a Claude Code con el pedido de diagnosticar sin escribir en la base |
| Postman: `ECONNREFUSED` con el contenedor arriba | Todavía en `starting`, `https` en vez de `http`, o el puerto no está publicado | Esperá `healthy`; revisá `baseUrl`; en `ps` tiene que figurar `0.0.0.0:8083->8083/tcp`. Si usás WSL, probá `127.0.0.1` en vez de `localhost` |
| Postman responde con el contenedor abajo | Otro proceso escucha en el 8083 | Encontralo con `lsof -i :8083` (o `netstat -ano \| findstr :8083`), cortalo y repetí la prueba |
| `401` con la clave correcta | Entorno no seleccionado, `apiKey` vacía, el request no hereda la auth, un espacio o salto de línea al copiar, o cambiaste la clave en `infra/.env` sin recrear el contenedor | Revisá en la consola de Postman (*View* > *Show Postman Console*) qué header salió. Después de cambiar el `.env`, `up -d` recrea el contenedor |
| Un cambio de código no se ve en el contenedor | Levantaste sin `--build` | `docker compose -f infra/docker-compose.yml up --build -d` |
| `415` en la foto | Body *raw* o `Content-Type` puesto a mano | *form-data* y sin header `Content-Type` |
| `400` en la foto | La key no se llama `archivo` o es de tipo *Text* | Key `archivo`, tipo *File* |
| `503` en la foto con modo `supabase` | Clave secreta de Storage mal o de otro proyecto, URL de Supabase mal, o el bucket tiene otro nombre | Los logs muestran operación, status y cuerpo de la respuesta de Storage (nunca la clave). El cuerpo dice qué falló |
| La `fotoUrl` no abre o muestra la foto vieja | Bucket no público o caché de la CDN | Revisá que el bucket sea público; agregá `?v=2` al final de la URL |
| `500 ERROR_INTERNO` | Excepción no controlada | Buscá el `correlationId` en los logs: ahí está la excepción. Si es `VehiculoInvalidoException` al leer, hay un dato en la base fuera de las invariantes, cargado a mano |
| Conteos distintos (11 en vez de 10) | Quedaron datos de pruebas anteriores o de otro integrante | Corré las consultas de 9.1 antes de seguir |

### 5.3 Cómo arreglar un defecto sin romper la prueba

1. Anotá el caso que falló y seguí con los bloques que no dependen de él. No arregles a mitad de un bloque.
2. Rama nueva desde `dev`: `git switch dev && git pull origin dev && git switch -c feature/vehiculos-fix-<tema>`. Si tenés algo sin commitear en la rama de la prueba, commitealo o guardalo con `git stash` antes.
3. Abrí `claude` en una terminal **con** las variables cargadas (no la limpia del compose) y pegá este prompt:

```text
Falla en la prueba de punta a punta de la etapa 9, caso <id del caso>.

Request: <método y ruta>, headers relevantes (sin el valor de X-Api-Key), cuerpo:
<cuerpo>
Esperado según docs/arquitectura.md sección <n>: <status y codigo>
Obtenido: <status>
<cuerpo de la respuesta, con cualquier secreto enmascarado>
correlationId: <valor>
Logs de ese correlationId:
<líneas de docker compose logs, con secretos enmascarados>

Las variables VEHICULOS_* están cargadas en esta terminal. No leas ni imprimas infra/.env ni el valor de ninguna variable (nada de env, printenv, cat de archivos con secretos, curl -v ni docker compose config sin --quiet). Pasá la clave a curl solo como variable de shell.

Trabajá en modo plan. Todavía no modifiques nada:
1. Reproducí la falla con curl contra el contenedor (docker compose -f infra/docker-compose.yml up --build -d).
2. Decime la causa con evidencia (archivo, línea, log) y si el error está en el código, en la configuración del contenedor, en el request o en el documento.
3. Proponé el arreglo mínimo y el test que lo demuestra, y esperá mi OK.

Después de mi OK: escribí primero el test y miralo fallar, corregí, corré mvn -pl vehiculos-service -am verify, reconstruí la imagen y repetí con curl el caso <id> y el resto de su bloque. No toques nada fuera de la falla: lo que veas para mejorar va a docs/mejoras.md. Al terminar, bajá el contenedor (docker compose -f infra/docker-compose.yml down).
```

4. Revisá el diff, commit, pull request a `dev` y merge.
5. Volvé a la terminal limpia, `git switch dev && git pull origin dev`, `up --build -d`, y repetí **el bloque entero** donde falló, no solo el caso. Si el arreglo tocó algo transversal (handler de errores, interceptor, configuración), repetí también A y B.

Si el arreglo es del documento y no del código, no hace falta rama de código: pedile a Claude Code que proponga el cambio en `docs/arquitectura.md` y aprobalo vos.

### 5.4 Cuando el que falla es Claude Code

- **Dale evidencia, no descripciones.** "No anda la foto" produce adivinanzas. Status, cuerpo, `correlationId` y líneas de log producen un diagnóstico.
- **Diagnóstico antes que código.** Si empieza a editar antes de explicar la causa, cortalo con `Esc` y pedile que trabaje en modo plan (`Shift+Tab` hasta que diga *plan mode*). Un arreglo sin causa confirmada suele tapar el síntoma.
- **Regla de dos intentos.** Si prueba el mismo arreglo dos veces sin evidencia nueva, cortalo con `Esc` y pedile la lista de hipótesis con qué evidencia confirmaría o descartaría cada una. Si sigue trabado, `/clear` y sesión nueva con un resumen corto: qué falló, qué se probó y qué se descartó. Una sesión nueva con ese resumen rinde más que una larga llena de intentos fallidos.
- **Deshacer.** `/rewind` (o `Esc` dos veces con el campo de texto vacío) restaura los archivos que Claude Code editó con sus herramientas de edición. No deshace lo que hizo por comandos de shell (`rm`, `mv`, `docker`, SQL): para eso, `git status`, `git diff` y `git restore <archivo>`. Hacé un commit antes de pedirle un arreglo riesgoso, así tenés un punto limpio al que volver.
- **Sesiones largas.** Si la sesión se alarga y empieza a olvidar cosas, usá `/compact` o `/clear`. `CLAUDE.md` se vuelve a leer, pero las reglas de manejo de secretos del prompt no están en `CLAUDE.md` completas: después de compactar, repetile el párrafo de secretos.
- **Pedidos de permiso.** Si te pide correr `cat infra/.env`, `env`, `printenv`, `curl -v` o `docker compose config` sin `--quiet`, rechazalo y decile por qué. Si un secreto ya apareció en la conversación, rotalo: la clave del rol con `ALTER ROLE vehiculos_app WITH PASSWORD '<nueva>';` desde el SQL Editor, la `sb_secret_` creando una nueva en *Project Settings* > *API Keys* y borrando la vieja, y `X-Api-Key` con `openssl rand -base64 32`. Actualizá `infra/.env`, recreá el contenedor y pasale los valores nuevos al equipo por el canal seguro.
- **"Los tests pasan" no alcanza.** Si dice que algo funciona, pedile el comando que corrió y su salida. Y si los tests pasan pero el caso sigue fallando en el contenedor, casi siempre es una imagen vieja (falta `--build`) o un request que le pega a otro proceso.
- **Se va del scope.** Si en medio de un arreglo propone refactors o mejoras, recordale la regla de `CLAUDE.md`: eso va a `docs/mejoras.md`.
- **Docker desde Claude Code.** Claude Code usa el mismo Docker que vos. Si sus comandos `docker` fallan, revisá que Docker Desktop esté abierto y que `docker ps` funcione en esa terminal sin `sudo`.

## 6. Hábitos que te ahorran problemas

Una rama y un pull request por etapa o por arreglo. Las ideas de mejora fuera de scope que Claude Code detecte van a `docs/mejoras.md`: revisalas al cerrar cada etapa y decidí cuáles pasan a trabajo real. Si Claude Code se desvía de las convenciones, corregí `CLAUDE.md` además del código, así no se repite. `verify` por defecto no necesita credenciales y es lo que tiene que pasar siempre; la suite `-Psupabase` usa solo el schema `vehiculos_test`. El compose se levanta desde una terminal sin las `VEHICULOS_*` cargadas, y Claude Code se abre desde una terminal con ellas. Si el proyecto se pausó por inactividad, reactivalo desde el dashboard antes de una demo. Cuando Claude Code te plantee decisiones abiertas, contestalas con tu criterio y anotá cuál elegiste y por qué: eso es lo que te van a preguntar en la defensa.
