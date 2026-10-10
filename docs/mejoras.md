# Mejoras fuera de scope

## 2026-10-09 · Etapa 5.1 (suite contra Supabase)

- **Lock para corridas concurrentes sobre `vehiculos_test`.** Hoy cada contexto hace `clean` + `migrate` al arrancar; si dos integrantes corren `-Psupabase` a la vez, uno le borra el schema al otro a mitad de la corrida. Mejora: tomar un `pg_advisory_lock` en la estrategia de limpieza y soltarlo al cerrar el contexto. Impacto: bajo (unas líneas en `PostgresDePrueba`); hoy se evita con la regla de que solo el responsable corre la suite.
- **La guarda podría comprobar el rol conectado.** `GuardaSchemaDePrueba` valida el schema configurado, pero no que el usuario no sea `postgres` (regla de `CLAUDE.md`). Mejora: en la estrategia de limpieza, `SELECT current_user` y rechazar `postgres`. Impacto: bajo; protege contra cargar credenciales de administrador por error.
- **Duración de `-Psupabase` (~3 min 40 s).** Casi todo es latencia: cada contexto tarda 50-60 s en arrancar (`clean` ~8 s, `migrate` ~15 s, validación de Hibernate) y cada test hace varios viajes a la base. Mejora posible: `clean` + `migrate` una sola vez por corrida y vaciar tablas con `TRUNCATE` entre JVM. Impacto: medio, ahorra ~1 min; no urge porque la suite es opt-in.
