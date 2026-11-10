# Seguridad

[English](../../SECURITY.md) | Español

## Reportar una vulnerabilidad

Si encontrás un problema de seguridad (fugas de datos, acceso no autorizado,
etc.), **no abras un issue público**. Reportalo de forma privada con
[GitHub Security Advisories](https://github.com/Lxchoooo/dannx/security/advisories/new)
e incluí:

- La versión de la app y el modelo del teléfono.
- Los pasos para reproducirlo.
- Qué impacto tiene (qué datos o funciones quedan expuestas).

Vas a recibir respuesta lo antes posible mientras se investiga y se prepara un
arreglo.

## Alcance

- La app DannX y sus builds publicados en Releases.

Fuera de alcance: problemas en servicios de terceros, en el sistema Android o en
otras apps.

## Buenas prácticas que ya aplica el proyecto

- La clave de servicio del backend nunca se usa en la app ni en el repo.
- Las escrituras en la base están limitadas por reglas del servidor a una sola
  cuenta de administrador.
- La firma de release vive como secreto de GitHub, no en el repositorio.
- El registro público de Supabase está deshabilitado: la app solo inicia sesión
  con la cuenta administradora.
- El esquema y las políticas viven en `docs/migraciones/`; `supabase/setup.sql`
  queda como referencia histórica. El workflow de migraciones verifica que
  `collections.is_public` exista, que RLS esté activo en colecciones e ítems y
  que las políticas usen esa columna.
- `main` no acepta borrados ni force-push; los PRs exigen los checks `checks` y
  `debug` (con bypass para el rol admin). `debug` corre solo en PRs. Las
  actions están fijadas a SHA y el repo exige SHA pinning.
- No hay deploy keys con escritura; los push usan tokens de GitHub.
- Los tokens de sesión viven en preferencias privadas excluidas de los backups
  del dispositivo.
- El APK de release no incluye logs de depuración.
- CI corre CodeQL, dependency review y validación del wrapper de Gradle. Los
  releases publican un checksum SHA-256 y una atestación de procedencia.

La checklist completa, incluidas las opciones de GitHub que se aplican fuera del
repositorio, está en
[`docs/designs/security-hardening.md`](../designs/security-hardening.md).
