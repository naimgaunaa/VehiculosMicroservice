# VehiculosMicroservice

Microservicio `vehiculos` del CRM de una agencia de autos usados (TP de Desarrollo de Aplicaciones II, UADE). Administra el inventario: alta de unidades, precio y disponibilidad, historial de precios y foto. Corre en el puerto 8083.

La especificación completa (modelo, API, reglas, base de datos, configuración y pruebas) está en [`docs/arquitectura.md`](docs/arquitectura.md).

## Stack

Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL en Supabase, Supabase Storage para las fotos, springdoc-openapi, JUnit 5, Mockito, AssertJ, Testcontainers, Maven y Docker Compose.

## Requisitos

- JDK 21 y Maven (el repo no trae wrapper).
- Docker abierto para los tests de integración (Testcontainers) y para correr el contenedor.

## Variables de entorno

Copiá `infra/.env.example` a `infra/.env` y completá los valores. `infra/.env` está en `.gitignore`: no se commitea ni se comparte por chat. La tabla de variables está en la sección 10 de `docs/arquitectura.md`.

Spring no lee `.env` por sí solo. Para cargar las variables en la terminal:

```bash
source scripts/cargar-env.sh
```

```powershell
.\scripts\cargar-env.ps1
```

## Comandos

| Qué | Comando |
|---|---|
| Compilar y testear (Testcontainers, sin credenciales) | `mvn -pl vehiculos-service -am verify` |
| Testear contra Supabase (schema `vehiculos_test`, con las variables cargadas) | `mvn -pl vehiculos-service -am verify -Psupabase` |
| Correr en local (perfil `local`, con las variables cargadas) | `mvn -pl vehiculos-service spring-boot:run` |
| Correr con Docker (con `infra/.env` completo) | `docker compose -f infra/docker-compose.yml up --build` |

## API

- Swagger UI: `http://localhost:8083/swagger-ui.html`
- OpenAPI: `http://localhost:8083/v3/api-docs` (se puede importar en Postman)
- Health: `http://localhost:8083/actuator/health`

Las lecturas son abiertas. `POST /vehiculos`, `PUT /vehiculos/{id}` y `PUT /vehiculos/{id}/foto` exigen el header `X-Api-Key`.
