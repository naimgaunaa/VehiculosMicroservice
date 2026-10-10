# Mejoras fuera de scope

## 2026-10-09 · Etapa 5.1 (suite contra Supabase)

- **Lock para corridas concurrentes sobre `vehiculos_test`.** Hoy cada contexto hace `clean` + `migrate` al arrancar; si dos integrantes corren `-Psupabase` a la vez, uno le borra el schema al otro a mitad de la corrida. Mejora: tomar un `pg_advisory_lock` en la estrategia de limpieza y soltarlo al cerrar el contexto. Impacto: bajo (unas líneas en `PostgresDePrueba`); hoy se evita con la regla de que solo el responsable corre la suite.
- **La guarda podría comprobar el rol conectado.** `GuardaSchemaDePrueba` valida el schema configurado, pero no que el usuario no sea `postgres` (regla de `CLAUDE.md`). Mejora: en la estrategia de limpieza, `SELECT current_user` y rechazar `postgres`. Impacto: bajo; protege contra cargar credenciales de administrador por error.
- **Duración de `-Psupabase` (~3 min 40 s).** Casi todo es latencia: cada contexto tarda 50-60 s en arrancar (`clean` ~8 s, `migrate` ~15 s, validación de Hibernate) y cada test hace varios viajes a la base. Mejora posible: `clean` + `migrate` una sola vez por corrida y vaciar tablas con `TRUNCATE` entre JVM. Impacto: medio, ahorra ~1 min; no urge porque la suite es opt-in.

## 2026-10-10 · Etapa 6 (alta y actualización)

- **Comparar hashes de la clave en vez de los bytes.** `MessageDigest.isEqual` es de tiempo constante respecto del contenido, pero termina antes si los largos difieren, así que deja inferir el largo de `VEHICULOS_CLAVE_ESCRITURA`. Mejora: comparar `SHA-256(recibida)` contra `SHA-256(configurada)` (calculado una vez al construir el interceptor). Impacto: bajo (dos líneas en `ClaveServicioInterceptor`); el riesgo real es mínimo con una clave de 32 caracteres o más.
- **Header `WWW-Authenticate` en el 401.** RFC 9110 pide que un 401 lo incluya. Mejora: agregar `WWW-Authenticate: ApiKey header="X-Api-Key"` en el handler de `ClaveServicioInvalidaException`. Impacto: bajo; ningún cliente del equipo lo usa hoy.
- **`campo` en `VehiculoInvalidoException`.** El 422 que nace en el dominio sale con el mensaje en `detail` y sin `errores`, porque la excepción no sabe qué campo falló. Hoy no se alcanza (el DTO cubre todas las reglas), pero si el DTO y el dominio se desalinean (por ejemplo, el máximo de `anio` desde 2027) la respuesta no cumpliría la regla 1. Mejora: que cada `throw` del dominio informe el campo y que el handler arme `errores`. Impacto: medio (9 lugares en `Vehiculo` y sus tests).

## 2026-10-10 · Etapa 7 (fotos)

- **Versionar la `fotoUrl` para esquivar la caché de la CDN.** Probado contra Supabase: después de borrar `{id}.png`, la URL pública siguió respondiendo 200 con la imagen vieja (`cf-cache-status: HIT`), mientras que el origen ya daba `NoSuchKey`. Lo mismo puede pasar al reemplazar una foto con la misma extensión: la URL no cambia y el navegador o la CDN muestran la anterior. Mejora: guardar la URL con un sufijo de versión (`?v={epoch}`) o un nombre de objeto con hash. Impacto: bajo en código (`SupabaseFotoStorage` y la comparación de URL en `FotoVehiculoService`); hoy el front podría ver la foto vieja durante el TTL de la CDN.
- **Multipart mal formado da 500.** Un cuerpo `multipart/form-data` que Tomcat no puede parsear lanza `MultipartException` (no la de tamaño) y cae en `ERROR_INTERNO`. Mejora: mapearla a `400 SOLICITUD_INVALIDA` en `ApiExceptionHandler`. Impacto: bajo (un handler y un test); solo lo dispara un cliente roto.
- **Truncar el cuerpo de error de Storage en el log.** El WARN de `SupabaseFotoStorage` vuelca el cuerpo completo de la respuesta (útil para ajustar el contrato). Si el gateway devolviera una página HTML larga, el log crece sin control. Mejora: cortar a ~500 caracteres. Impacto: mínimo.
