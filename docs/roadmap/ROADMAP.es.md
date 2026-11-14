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

## 1.6.0 (en preparación)

- [x] **Actualizador dentro de la app** que consulta la última release,
      descarga el APK y abre el instalador.
- [x] **Paginación y caché incremental**: 30 fondos por página, búsqueda y
      filtros en el servidor, y caché offline de 400 fondos.
- [x] **Sorprendeme** para abrir un fondo aleatorio del filtro actual.
- [x] **Repaso de accesibilidad**: TalkBack, áreas táctiles y fuentes grandes.
- [x] **Filtros por etiquetas en Inicio**, con una sola escritura para las
      etiquetas escritas en mayúsculas o minúsculas.
- [x] Notas de release en inglés desde `docs/releases/`.
- [x] CI en verde para la rama de release.
- [x] Tag y publicación de `v1.6.0`.

## 1.5.0

- [x] Traducción completa al inglés y desenfoque progresivo en el editor.
- [x] Pantalla Acerca de, acceso a privacidad en Ajustes y enlaces públicos del
      proyecto.
- [x] Caché del feed acotada a 400 fondos y 100 colecciones.
- [x] CodeQL, dependency review, validación del wrapper, checksums y atestaciones
      de procedencia.
- [x] Acciones en lote atómicas con RPC de Supabase.
- [x] Papelera con restauración, borrado definitivo y purga automática a los 30
      días.
- [x] Vista previa de portada en el editor de Contenido.
- [x] Tag y publicación de `v1.5.0`.

## Siguiente

- [ ] **Baseline profile y Macrobenchmark** para mejorar el arranque y el scroll
      en equipos de gama baja.

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
- [x] Notas de release generadas desde un archivo versionado en inglés.

## Backend y seguridad

- [ ] Tests de RLS que demuestren que un usuario anónimo no puede escribir.
- [x] Políticas de Storage y limpieza de imágenes huérfanas de la papelera.
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
Leé [CONTRIBUTING.es.md](../i18n/CONTRIBUTING.es.md) antes de mandar un pull request y
reportá problemas de seguridad con [SECURITY.es.md](../i18n/SECURITY.es.md).
