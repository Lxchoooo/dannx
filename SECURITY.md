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

Fuera de alcance: problemas en servicios de terceros, en el sistema Android o en
otras apps.

## Buenas prácticas que ya aplica el proyecto

- La clave de servicio del backend nunca se usa en la app ni en el repo.
- Las escrituras en la base están limitadas por reglas del servidor a una sola
  cuenta de administrador.
- La firma de release vive como secreto de GitHub, no en el repositorio.
