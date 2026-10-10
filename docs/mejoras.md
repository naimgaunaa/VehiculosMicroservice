# Mejoras fuera de scope

## 2026-10-09 · Etapa 5.1 (suite contra Supabase)

- **Lock para corridas concurrentes sobre `vehiculos_test`.** Hoy cada contexto hace `clean` + `migrate` al arrancar; si dos integrantes corren `-Psupabase` a la vez, uno le borra el schema al otro a mitad de la corrida. Mejora: tomar un `pg_advisory_lock` en la estrategia de limpieza y soltarlo al cerrar el contexto. Impacto: bajo (unas líneas en `PostgresDePrueba`); hoy se evita con la regla de que solo el responsable corre la suite.
- **La guarda podría comprobar el rol conectado.** `GuardaSchemaDePrueba` valida el schema configurado, pero no que el usuario no sea `postgres` (regla de `CLAUDE.md`). Mejora: en la estrategia de limpieza, `SELECT current_user` y rechazar `postgres`. Impacto: bajo; protege contra cargar credenciales de administrador por error.
- **Duración de `-Psupabase` (~3 min 40 s).** Casi todo es latencia: cada contexto tarda 50-60 s en arrancar (`clean` ~8 s, `migrate` ~15 s, validación de Hibernate) y cada test hace varios viajes a la base. Mejora posible: `clean` + `migrate` una sola vez por corrida y vaciar tablas con `TRUNCATE` entre JVM. Impacto: medio, ahorra ~1 min; no urge porque la suite es opt-in.

## 2026-10-10 · Etapa 6 (alta y actualización)

- **Comparar hashes de la clave en vez de los bytes.** `MessageDigest.isEqual` es de tiempo constante respecto del contenido, pero termina antes si los largos difieren, así que deja inferir el largo de `VEHICULOS_CLAVE_ESCRITURA`. Mejora: comparar `SHA-256(recibida)` contra `SHA-256(configurada)` (calculado una vez al construir el interceptor). Impacto: bajo (dos líneas en `ClaveServicioInterceptor`); el riesgo real es mínimo con una clave de 32 caracteres o más.
- **Header `WWW-Authenticate` en el 401.** RFC 9110 pide que un 401 lo incluya. Mejora: agregar `WWW-Authenticate: ApiKey header="X-Api-Key"` en el handler de `ClaveServicioInvalidaException`. Impacto: bajo; ningún cliente del equipo lo usa hoy.
- **`campo` en `VehiculoInvalidoException`.** El 422 que nace en el dominio sale con el mensaje en `detail` y sin `errores`, porque la excepción no sabe qué campo falló. Hoy no se alcanza (el DTO cubre todas las reglas), pero si el DTO y el dominio se desalinean (por ejemplo, el máximo de `anio` desde 2027) la respuesta no cumpliría la regla 1. Mejora: que cada `throw` del dominio informe el campo y que el handler arme `errores`. Impacto: medio (9 lugares en `Vehiculo` y sus tests).
