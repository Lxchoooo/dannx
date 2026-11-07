# Seguridad

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
- La configuración del backend descrita en `supabase/setup.sql`.

Fuera de alcance: problemas en Supabase, en el sistema Android o en otras apps
de terceros.

## Buenas prácticas que ya aplica el proyecto

- La clave `service_role` de Supabase nunca se usa en la app ni en el repo.
- Las escrituras en la base están limitadas por RLS a una sola cuenta de
  administrador.
- La firma de release vive como secreto de GitHub, no en el repositorio.
