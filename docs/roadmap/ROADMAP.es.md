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

## 1.8.0

- [x] **Vista previa de pantalla de bloqueo** en el detalle, con la hora, la
      fecha y accesos genéricos, respetando el Modo Lite.
- [x] **Fondos relacionados** por categoría y etiquetas, con un tope de cinco y
      navegación que reemplaza la entrada actual.
- [x] **Módulo de Macrobenchmark** con mediciones de arranque y scroll, y una
      primera medición de referencia.
- [x] Correcciones de paginación del feed, del User-Agent de descargas y del
      bucket de Storage en las migraciones.
- [ ] Tag y publicación de `v1.8.0`.

## 1.7.0

- [x] **Filtros por etiquetas en Inicio**, con una sola escritura para las
      etiquetas escritas en mayúsculas o minúsculas y una selección que se
      conserva al navegar.
- [x] Estabilidad y mantenimiento en el servidor y en la app.
- [x] Tag y publicación de `v1.7.0`.

## 1.6.0

- [x] **Actualizador dentro de la app** que consulta la última release,
      descarga el APK y abre el instalador.
- [x] **Paginación y caché incremental**: 30 fondos por página, búsqueda y
      filtros en el servidor, y caché offline de 400 fondos.
- [x] **Sorprendeme** para abrir un fondo aleatorio del filtro actual.
- [x] **Repaso de accesibilidad**: TalkBack, áreas táctiles y fuentes grandes.
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
  - [x] Módulo `:benchmark` con mediciones de arranque y scroll del feed, y un
        job manual en CI que las corre en un emulador.
  - [x] Primera medición: 757 ms de mediana en arranque en frío sobre el
        emulador API 34, guardada como referencia en
        [`benchmarking.md`](../designs/benchmarking.md).
  - [x] Generar el perfil contra una variante sin minificar y reemplazar el
        `app/src/main/baseline-prof.txt` escrito a mano: el arranque en frío
        bajó de 973 ms a 729 ms en el mismo emulador
        ([`benchmarking.md`](../designs/benchmarking.md)).

## Producto

- [ ] **Panel web de administración** para gestionar fondos sin usar la app.
- [ ] **Release firmado distribuido fuera de GitHub**, con verificación de
      checksum y atestación.
- [ ] **Widgets y rotación automática**: fondo del día o cambio cada pocas horas.
- [x] **Más idiomas**, empezando por portugués y francés.

## Calidad y CI

- [x] Mapping de R8 publicado como artifact de CI.
- [x] Tests instrumentados para aplicar un fondo y para la selección de
      favoritos.
- [ ] Tests instrumentados para descargar y para el editor.
- [x] Screenshot tests con Roborazzi: bienvenida, Sobre, señuelo y estados en
      los 4 idiomas y claro/oscuro; `verifyRoborazziDebug` corre en la CI y los
      dorados se regeneran con el workflow `Record screenshots`.
- [x] `actionlint` para los workflows y `shellcheck` para los scripts.
- [x] `zizmor` para GitHub Actions.
- [x] Notas de release generadas desde un archivo versionado en inglés.

## Backend y seguridad

- [x] Tests de RLS que demuestren que un usuario anónimo no puede escribir.
- [x] Políticas de Storage y limpieza de imágenes huérfanas de la papelera.
- [x] Rate limiting: sin limitador de escrituras (solo el admin puede
      escribir) y con `statement_timeout` + medición `pg_stat_statements`
      ([diseño y decisión](../designs/rate-limiting.md)).
- [x] Guía de instalación de Supabase actualizada
      ([`supabase/README.md`](../supabase/README.md)).
- [x] Secret scanning, push protection y reporte privado de vulnerabilidades
      activados en los settings de GitHub.
- [x] Limitar las GitHub Actions a las de GitHub y creadores verificados, más
      los dos terceros que se usan.

## No planeado

- Analítica, publicidad o SDKs de seguimiento.
- Registro público en Supabase.
- Poner la clave de servicio en la app o en el repositorio.

## Cómo ayudar

Abrí un [issue](https://github.com/Lxchoooo/dannx/issues) con la idea o el bug.
Leé [CONTRIBUTING.es.md](../i18n/CONTRIBUTING.es.md) antes de mandar un pull request y
reportá problemas de seguridad con [SECURITY.es.md](../i18n/SECURITY.es.md).
