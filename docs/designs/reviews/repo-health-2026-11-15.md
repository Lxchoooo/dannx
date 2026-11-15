# Salud del repositorio — 2026-11-15

- **Rama revisada:** `main` en `f73129b` (sin cambios pendientes).
- **Alcance:** código de la app, esquema y políticas de Supabase, workflows de CI,
  scripts, documentación y configuración del repositorio en GitHub.
- **Tipo:** informe. No incluye cambios de código.

## Veredicto

El proyecto está en buen estado: la app compila, lint y los 180 tests unitarios
pasan, hay 8 tests instrumentados en verde, y la seguridad del backend está
resuelta con más rigor que en la mayoría de proyectos de este tamaño (RLS en
todas las tablas, política de administrador centralizada, pruebas que intentan
escribir como `anon` y fallan).

No encontré ningún problema bloqueante ni ninguna vulnerabilidad explotable. Lo
que sigue son deudas de mantenimiento, dos detalles de CI y documentación que
quedó desactualizada respecto de la realidad.

## Cómo se revisó

- Lectura completa de: `AndroidManifest.xml`, `app/build.gradle.kts`,
  `gradle/libs.versions.toml`, los 7 workflows, los 3 scripts, `supabase/setup.sql`
  y las 12 migraciones, y los archivos del núcleo de datos
  (`WallsApi`, `ApiHttp`, `Postgrest`, `FeedQuery`, `FeedCache`, `Prefs`,
  `RetryPolicy`, `DannXError`, `UploadImage`, `GitHubUpdateRepository`,
  `WallpaperFiles`, `MainViewModel`, `SharedImageActivity`).
- Conteo y muestreo del resto de `ui/` (14.058 líneas) para medir tamaño y
  responsabilidades.
- Verificación del estado real de GitHub con la API (rulesets, secret scanning,
  CodeQL, Dependabot, permisos de Actions) y de las últimas corridas de CI,
  incluida la corrida fallida `43631608039`.
- Versiones publicadas en Google Maven y Maven Central para medir dependencias.
- **No se pudo verificar:** compilación y tests en local (este equipo no tiene
  JDK ni Android SDK; el proyecto se compila solo en CI, como ya documenta
  `docs/designs/ci-optimization.md`), el estado en vivo de la base de datos
  (solo se leyó el SQL del repositorio) y el límite real de escrituras que
  impone el plan de Supabase.

## Lo que está bien y no hay que tocar

- **RLS completa.** Las 9 tablas tienen `enable row level security`. Las
  políticas usan `(select auth.uid())` para evaluar la identidad una sola vez,
  `is_admin()` es `security definer` con `set search_path = public, pg_temp`,
  y las funciones revocan `public` y conceden solo a `authenticated`.
- **Pruebas de seguridad reales.** `scripts/sql/verify_anon.sql` no comprueba
  metadatos: intenta insertar en `wallpapers`, `collections`,
  `collection_items` y `storage.objects` como rol `anon` y exige que falle.
  `apply-migration.yml` lo corre en cada migración.
- **Sin inyección en PostgREST.** `postgrestArray` escapa `\` y `"`, y todo
  valor pasa por `encodeQueryValue`. Las etiquetas con comas o comillas no
  rompen el parámetro.
- **Escrituras en disco cuidadas.** `FeedCache.writeAtomically` usa archivo
  temporal + `ATOMIC_MOVE`; la descarga a Galería usa `IS_PENDING` y borra la
  fila de MediaStore si falla la escritura.
- **Actualizador con integridad.** Verifica el SHA-256 publicado en la release,
  corta la descarga si el tamaño no coincide y limpia el `.part` en cualquier
  error.
- **Sesión fuera de los backups.** `dannx_session.xml` está excluido tanto en
  `backup_rules.xml` como en `data_extraction_rules.xml`, y `Prefs.migrateSession`
  limpia las claves de la versión anterior.
- **Tests.** 180 casos unitarios (65 sobre `MainViewModel`), 8 instrumentados
  con emulador cacheado, y `.editorconfig`/`.gitattributes` para diffs estables.
- **CI.** Todas las acciones fijadas por SHA, `zizmor`, `actionlint`,
  `shellcheck`, CodeQL con build real, dependency review, validación del
  wrapper, mapping de R8 y atestación de procedencia. La documentación de
  seguridad y de CI es honesta sobre lo medido.
- **i18n real.** 499 strings, `values` + `values-es`, un solo literal de texto
  hardcodeado en toda la UI (una etiqueta de tag, correcto por diseño).
- **Dependencias al día**, salvo AGP (ver H6).

## Hallazgos

| ID | Severidad | Área | Resumen |
| --- | --- | --- | --- |
| H1 | Media | CI | Un PR que solo toca docs queda con los checks requeridos en "Expected" |
| H2 | Media | CI | El keystore de release se descifra en el job `debug` de cualquier PR |
| H3 | Media | Código | `MainViewModel` concentra 3.566 líneas, 71 campos y 75 acciones |
| H4 | Media | Backend | Sin rate limiting en escrituras |
| H5 | Baja | Docs | Roadmap y guía de seguridad describen un estado que ya cambió |
| H6 | Baja | Deps | AGP 4 versiones menores atrás; el PR de Dependabot se cerró sin nota |
| H7 | Baja | Código | `loadMore` puede colar una página de otro filtro y salta fondos |
| H8 | Baja | CI | El job `instrumented` es el único que no valida el wrapper |
| H9 | Baja | Docs | El README promete capturas "con la próxima versión" desde 1.5.0 |
| H10 | Nit | Código | Parámetros `token` muertos en `WallsApi` |
| H11 | Nit | Scripts | `release_notes.py` busca `## sin publicar`; el CHANGELOG dice `## Unreleased` |
| H12 | Nit | Código | KDoc duplicado en `RetryPolicy` |
| H13 | Nit | Código | `errorFor` mete 120 caracteres del cuerpo del servidor en el mensaje |

### H1 — Los checks requeridos no cubren los PR de solo documentación

**Media.** `.github/workflows/android.yml:7-14` ignora `**.md`, `docs/**` y
`LICENSE` en `pull_request`. El ruleset `main protegida` exige los checks
`checks` y `debug`. Si un PR solo toca documentación, el workflow no se crea y
GitHub deja esos dos checks en "Expected" de forma indefinida: el PR no se puede
mergear por la vía normal. Hoy no molesta porque el rol Admin tiene
`bypass_mode: always`, pero cualquier colaborador que mande una corrección de
docs queda trabado sin entender por qué.

**Fix:** un job "lleno" que siempre reporte los mismos contextos cuando el
workflow se saltea, o sacar `debug` de los checks requeridos y dejar solo
`checks`. La primera opción conserva la compuerta real y no obliga a compilar
para un cambio de texto.

**Nota:** deducido de la configuración, no reproducido con un PR de prueba.

### H2 — El keystore de release se descifra en builds de PR

**Media.** `.github/workflows/android.yml:58` restaura
`ANDROID_KEYSTORE_BASE64` en el job `debug`, que corre en todo PR, y
`app/build.gradle.kts:70` firma el APK de debug con esa misma clave. En un PR
desde un fork los secrets no llegan, pero en un PR desde una rama del propio
repositorio sí: cualquier código de esa rama se ejecuta en el runner con la
clave y su contraseña en el entorno, y el APK firmado se sube como artifact
descargable.

Hoy el único que puede crear ramas en el repo es el mantenedor, así que el
riesgo real es bajo. Pasa a ser serio el día que se agregue un colaborador.

**Fix:** restaurar el keystore solo en `main` y en tags, y en PR usar una clave
de depuración propia (o ninguna). Si se quiere conservar "instalar encima de la
versión publicada", publicar el artifact solo cuando el build venga de `main`.

### H3 — `MainViewModel` es el centro de todo

**Media.** `MainViewModel.kt` tiene 3.566 líneas, 75 funciones públicas y más de
200 llamadas a `.copy()` sobre un `UiState` de 49 campos. Cubre feed, filtros,
detalle, colecciones, sesión, subida, Estudio, etiquetas, papelera, ajustes y
actualizador. `StudioScreen.kt:142` tiene un composable de 609 líneas y
`WallsApi.kt` llega a 1.630.

No es un bug: los tests unitarios cubren bien el ViewModel. El costo es que cada
feature toca tres o cuatro archivos grandes y que un cambio en el estado obliga
a revisar más de 200 sitios.

**Fix sugerido, por pasos y sin reescribir:** extraer la lógica del Estudio
(`createCategory`, `updateCategory`, `reorderFeatured`, papelera, etiquetas) a un
`StudioViewModel` o a objetos de caso de uso, y dejar `MainViewModel` con feed,
filtros y sesión. Después, partir `StudioScreen` en un archivo por pestaña.

### H4 — Sin rate limiting en escrituras

**Media.** No hay ningún límite en el repositorio: `grep` de
`rate.limit|throttle` en `supabase/`, `docs/migraciones/` y `scripts/` no
devuelve nada. La `anon key` viaja en el APK, es pública por diseño y permite
lecturas sin autenticación. Las escrituras están protegidas por RLS y
`is_admin()`, así que no se puede corromper datos, pero sí generar carga y costo
con lecturas o intentos de escritura fallidos.

El roadmap ya lo tiene anotado como pendiente (`ROADMAP.md:75`); lo confirmo
como real.

**Fix:** un trigger que cuente escrituras por ventana y rechace el exceso, o
configurar el límite de la API de Supabase. Un `statement_timeout` corto para
`anon` también ayuda.

### H5 — La documentación describe un estado anterior

**Baja.** Tres documentos quedaron atrás:

- `docs/designs/security-hardening.md:26-35` lista como pendientes secret
  scanning, push protection, reporte privado de vulnerabilidades, Dependabot
  security updates, SHA pinning obligatorio y `GITHUB_TOKEN` de solo lectura.
  Todo eso ya está activo (ver tabla más abajo). Lo único que sigue pendiente
  es limitar las acciones permitidas.
- `docs/roadmap/ROADMAP.es.md:78` y `ROADMAP.md:77` mantienen sin marcar el
  mismo ítem de seguridad.
- `docs/roadmap/ROADMAP.md:65` pide tests instrumentados "para aplicar,
  descargar, favoritos y el editor": aplicar y favoritos ya existen
  (`WallpaperApplyFlowTest`, `FavoritesSelectionTest`); faltan descarga y editor.

**Fix:** marcar lo hecho y dejar el ítem de instrumentados como parcial.

### H6 — AGP cuatro versiones menores atrás

**Baja.** `gradle/libs.versions.toml:2` fija `agp = "9.1.0"`. La última estable
es 9.5.1 y Dependabot ya abrió el PR #37 con ese bump, que se cerró sin merge ni
comentario. El resto del catálogo está en la última estable o a un parche de
distancia (Compose BOM 2026.09.00, navigation 2.10.2, OkHttp 5.5.1, Coil 2.7.0).

**Fix:** decidir el bump de AGP con una corrida de CI, o dejar una nota en el PR
explicando por qué se descartó, para no repetir el análisis el mes que viene.

### H7 — `loadMore` puede mezclar dos filtros y saltear fondos

**Baja.** `MainViewModel.kt:515-518` compara `query`, `category` y `tag` contra
el estado actual antes de aplicar la página nueva, pero no compara `tags`, que es
el filtro real desde 1.7.0. Cuando cambia una etiqueta, `reloadFeed` cancela el
job, así que la ventana es chica, pero el chequeo está incompleto.

Además la paginación usa `offset = wallpapers.size` mientras el cliente descarta
elementos después de recibirlos (archivados, en papelera, sin `storagePath`):
cada descarte corre el offset y puede saltear un fondo. No duplica gracias a
`distinctBy`, pero sí pierde elementos.

**Fix:** incluir `tags` en la comparación y calcular el offset con la cantidad
de filas que devolvió el servidor, no con el tamaño de la lista visible. Un
test en `MainViewModelTest` con dos etiquetas distintas lo cubre.

### H8 — El job `instrumented` no valida el wrapper

**Baja.** Los jobs `debug` (`android.yml:41`), `checks` (`:112`) y `build`
(`:234`) ejecutan `gradle/actions/wrapper-validation`, pero `instrumented`
(`:144`) no, y es el que más código de terceros descarga (SDK, emulador, AVD)
antes de compilar.

**Fix:** agregar el paso, igual que en los otros tres.

### H9 — Las capturas prometidas nunca llegaron

**Baja.** `README.md:31-32` y `README.es.md:31-32` dicen que las capturas se
publican "con la próxima versión" y todavía muestran
`docs/screenshots/demo-placeholder.svg`. Se publicaron 1.6.0 y 1.7.0.
`docs/screenshots/README.md:25` habla del "set de 1.5.0".

**Fix:** o se agregan las cuatro capturas de la tabla, o se cambia el texto para
no prometer fechas.

### Nits

- **H10** `WallsApi.kt:1497,1527,1568`: `uploadObject`, `deleteObject` e
  `insertWallpaper` reciben `token: String` y no lo usan; el lambda de
  `executeAuthorized` lo oculta. `requireToken()` en `uploadWallpaper` queda solo
  como falla temprana. Conviene borrar el parámetro para que no confunda.
- **H11** `scripts/release_notes.py:46` busca `## sin publicar`, pero
  `CHANGELOG.md:7` dice `## Unreleased`: la rama de respaldo nunca coincide. Solo
  afecta builds sin tag, que igual caen al texto por defecto.
- **H12** `RetryPolicy.kt:45-49`: dos bloques KDoc consecutivos para la misma
  función; el primero es una frase suelta.
- **H13** `ApiHttp.kt:14`: el mensaje de `Unexpected` incluye hasta 120
  caracteres del cuerpo del servidor. No llega a la UI (se traduce a
  `error_unexpected`), pero sí a `Log.w` cuando falla una subida.

## Configuración real de GitHub

Verificada con la API el 2026-11-15, contra lo que dicen los documentos:

| Elemento | Documento | Real |
| --- | --- | --- |
| Secret scanning | por activar | **activo** |
| Push protection | por activar | **activo** |
| Reporte privado de vulnerabilidades | por activar | **activo** |
| Dependabot security updates | por activar | **activo**, sin alertas abiertas |
| SHA pinning obligatorio | por activar | **activo** |
| `GITHUB_TOKEN` por defecto | por activar | **read** |
| Acciones permitidas | "solo seleccionadas" | `all` (sigue pendiente) |
| Validity checks de secret scanning | — | desactivado |
| Patrones no proveedor (secret scanning) | — | desactivado |
| Ruleset `main protegida` | configurado | activo: exige `checks` y `debug`, bloquea borrado y force-push, Admin con bypass `always` |
| Alertas de CodeQL / Dependabot / secretos | — | 0 abiertas |

CI: última corrida de `main` en verde. La corrida `43631608039` falló solo en
`instrumented` (3 tests de `HomeTagFilterTest`), y el commit siguiente
`f73129b` los reescribió contra `HomeTagFilterRow`; desde entonces pasa.

## Plan propuesto

1. **PR de documentación (30 min):** H5 y H9, más H11. Sin riesgo, sin tocar
   código.
2. **PR de CI (1 h):** H1, H2 y H8. Verificable en la misma corrida del PR.
3. **PR de código (1–2 días):** H7 con tests, H3 por partes, H10/H12/H13 de
   arrastre.

H4 (rate limiting) merece su propio diseño antes de escribir SQL.
