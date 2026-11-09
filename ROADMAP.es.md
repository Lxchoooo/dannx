# Hoja de ruta

[English](ROADMAP.md) | Español

DannX es una app de fondos de pantalla pequeña y centrada en la privacidad. Esta
es una lista de prioridades, no una promesa de fechas. Los ítems avanzan cuando
están probados y cuando hay tiempo para mantenerlos.

## Leyenda

- **Hecho**: implementado en el repositorio.
- **Siguiente**: el foco actual.
- **Planeado**: aceptado, pero sin empezar.
- **Explorando**: necesita una decisión de diseño primero.

## 1.4.0 (en preparación)

- [x] Traducción completa al inglés y desenfoque progresivo en el editor.
- [x] Pantalla Acerca de, acceso a privacidad en Ajustes y enlaces públicos del
      proyecto.
- [x] Caché del feed acotada a 400 fondos y 100 colecciones.
- [x] CodeQL, dependency review, validación del wrapper, checksums y atestaciones
      de procedencia.
- [ ] CI en verde para la rama de release.
- [ ] Capturas para el README.
- [ ] Tag y publicación de `v1.4.0`.

## Siguiente

- [ ] **Baseline profile y Macrobenchmark** para mejorar el arranque y el scroll
      en equipos de gama baja.
- [ ] **Actualizador dentro de la app** que consulte la última release, compare
      versiones y abra la descarga. Hoy Ajustes abre la página de releases.
- [ ] **Paginación y caché incremental del feed** para no descargar y guardar
      todos los fondos de una vez.
- [ ] **Repaso de accesibilidad**: TalkBack, contraste, fuentes grandes y áreas
      táctiles.

## Producto

- [ ] **Panel web de administración** para gestionar fondos sin usar la app.
- [ ] **Release firmado distribuido fuera de GitHub**, con verificación de
      checksum y atestación.
- [ ] **Widgets y rotación automática**: fondo del día o cambio cada pocas horas.
- [ ] **Más idiomas**, empezando por portugués y francés.

## Calidad y CI

- [x] Mapping de R8 publicado como artifact de CI.
- [ ] Tests instrumentados para aplicar, descargar, favoritos y el editor.
- [ ] Screenshot tests (Roborazzi o Paparazzi) para detectar cambios visuales.
- [ ] Chequeos de `actionlint` y `zizmor` para GitHub Actions.
- [ ] Notas de release generadas desde el changelog o los commits.

## Backend y seguridad

- [ ] Tests de RLS que demuestren que un usuario anónimo no puede escribir.
- [ ] Políticas de Storage y limpieza de imágenes huérfanas.
- [ ] Rate limiting para escrituras.
- [ ] Guía actualizada de Supabase en `docs/`.
- [ ] Secret scanning, push protection y reporte privado de vulnerabilidades
      activados en los settings de GitHub.

## No planeado

- Analítica, publicidad o SDKs de seguimiento.
- Registro público en Supabase.
- Poner la clave de servicio en la app o en el repositorio.

## Cómo ayudar

Abrí un [issue](https://github.com/Lxchoooo/dannx/issues) con la idea o el bug.
Leé [CONTRIBUTING.es.md](CONTRIBUTING.es.md) antes de mandar un pull request y
reportá problemas de seguridad con [SECURITY.es.md](SECURITY.es.md).
