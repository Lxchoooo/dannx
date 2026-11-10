# Reglas Permanentes del Proyecto (Privacidad y Textos Públicos)

REGLA PERMANENTE PARA TODOS LOS TRABAJOS FUTUROS: mensajes de commit, pull requests y textos públicos.

El repositorio es público, así que todo lo que escribas (commits, descripciones de pull requests, notas de versión, comentarios nuevos en el código, nombres de archivos nuevos) lo puede leer cualquiera. Escribilo siempre de forma general y breve.

## 1. Títulos

- Cortos, en español o inglés según el caso dado, con una frase genérica sobre el área que se tocó. Sin prefijos técnicos.
- Usá solo estas categorías u otras igual de generales:
  - Mejoras en la interfaz
  - Mejoras en la navegación
  - Mejoras en las animaciones
  - Mejoras de rendimiento
  - Mejoras de estabilidad
  - Mejoras en la descarga y aplicación de fondos
  - Mejoras en colecciones y favoritos
  - Mejoras en el buscador
  - Mejoras en las actualizaciones de la app
  - Mejoras en ajustes y accesibilidad
  - Actualización de textos y traducciones
  - Actualización de documentación
  - Mantenimiento interno
- Si un cambio toca varias áreas, usá un título más amplio, por ejemplo "Mejoras generales de la app".

## 2. Cuerpo del commit o de la pull request

- Vacío, o de una o dos líneas generales (por ejemplo, "Ajustes visuales y correcciones menores").
- Sin listas detalladas de cambios.

## 3. Qué NUNCA debe aparecer en ningún texto público

- Todo lo relacionado con acceso o administración: pantallas ocultas o secretas, gestos o toques especiales, tiempos o cantidades de toques, paneles de administración, inicio de sesión, credenciales, roles, permisos, políticas, migraciones, tablas, almacenamiento o claves.
- El nombre del servicio o de la base de datos que usa el servidor.
- Detalles de seguridad: qué se corrigió, por qué, qué riesgo había o cómo se explotaba. Si el cambio es de seguridad, el título es "Mejoras de estabilidad" o "Mantenimiento interno".
- Cómo funciona por dentro una función sensible (papelera, borrado, subida de fondos, acceso).
- Nombres de funciones, de clases o de archivos internos, números de línea, cantidades, tiempos o valores exactos.
- Quién pidió el cambio, qué se habló o cómo se decidió, y cualquier mención de herramientas, conversaciones o pruebas del usuario.

## 4. Notas de versión

- En español e inglés, con frases generales sobre lo que mejora para quien usa la app (por ejemplo, "Mejoras de estabilidad y de rendimiento"). Sin detallar funciones ocultas ni cambios internos.

## 5. Comentarios y nombres nuevos en el código

- Comentarios cortos y neutros que expliquen qué hace el código, sin describir cómo se llega a pantallas ocultas ni mecanismos de acceso. Nombres de archivos y de variables nuevos de forma neutra.

## 6. Registro privado

- Mantené un archivo de notas con el detalle real de cada cambio FUERA del repositorio. Usá la ruta `~/.dannx_private_notes.md` (en la raíz del usuario, nunca dentro de la carpeta del proyecto). Cada entrada con fecha, el título genérico que usaste y el detalle real.

## 7. Antes de cada commit

- Releé el título y la descripción y verificá que no rompan la regla 3. Si dudás, usá el texto más genérico posible.
- Si te pido algo que contradice esta regla, avisame antes de hacerlo.
