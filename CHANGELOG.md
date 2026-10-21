# Changelog

Registro de los cambios hechos en DannX. Se actualiza al cerrar cada bloque de
trabajo (fases de la hoja de ruta). La app todavía no tiene versiones públicas,
así que todo vive bajo "Sin publicar" hasta el primer tag.

## Sin publicar

### Fase 3 — Robustez

**Errores y red**
- Errores de red tipados (`DannXError`): sin conexión, servidor caído, sesión
  vencida o fallo inesperado, cada uno con su mensaje en español.
- Reintentos con espera exponencial (400 ms, 800 ms, 1600 ms) solo en fallos
  transitorios; nunca en errores 4xx ni en sesión vencida.
- Timeouts ajustados: 15 s de conexión, 45 s de lectura, 120 s de escritura.
- Ante un 401 se renueva el token una sola vez y se reintenta la petición; si
  vuelve a fallar, la sesión se cierra limpiamente con aviso.
- Subida de imágenes en streaming: la imagen se lee del URI en bloques y ya no
  se cargan hasta 25 MB en memoria.

**Estados de pantalla**
- `LoadState` sellado (cargando, contenido, vacío, error) reemplaza a los
  booleanos sueltos de carga y error.
- Cada pantalla muestra los cuatro estados: skeleton liviano sin animación,
  contenido, vacío con texto útil y error con botón "Reintentar".
- Textos nuevos en `strings.xml`.

**Sin conexión y caché**
- El último feed y las colecciones se guardan en disco (JSON en `cacheDir`).
- Sin red se muestra lo guardado con un aviso discreto "Sin conexión".
- Descargar, aplicar y compartir se desactivan sin red; favoritos y
  colecciones locales siguen funcionando.
- Al volver a la app se refresca una sola vez y solo si hay red.

**Navegación y estado**
- `launchSingleTop` en el detalle y en las colecciones: ya no se apilan
  pantallas con doble toque.
- Filtros, búsqueda y la imagen seleccionada en Estudio se guardan con
  `SavedStateHandle`: sobreviven a la rotación y a que el sistema mate la app.
- El refresh concurrente se cancela: no se apilan requests ni estados.

**Pruebas**
- El ViewModel ya no depende de Android: se prueba en la JVM con fakes.
- Tests nuevos: refresh cancelado, error transitorio con reintento, 401 con
  refresh, caché offline, descarga y duplicados, y el `RetryPolicy`.

### Fase 4 (adelantada) — Rendimiento

- El estado de las acciones (descargar, aplicar, compartir) vive en su propio
  `StateFlow`: el progreso ya no recompone toda la app.
- Modelos y `UiState` marcados como inmutables.
- El carrusel del hero lee su estado solo en la fase de dibujo.
- Requests de imagen memorizadas; hero y portadas con tamaño explícito.
- Gradientes memorizados, recorte redundante eliminado y barra inferior sin
  animación en el cambio de pestaña.
- Perfil de base con `profileinstaller` y reglas manuales.
- Overlay de FPS: una actualización por segundo.
- Reportes del compilador de Compose publicados en cada CI (artifact
  `compose-reports`): con ellos se confirmó que `MainViewModel` era inestable y
  eso invalidaba el skip de la app entera.
- `MainViewModel` marcado como estable y precarga de miniaturas una sola vez
  por carga del feed (no se relanza al volver a la pestaña).
- RGB_565 para miniaturas queda como opción documentada, apagada por defecto:
  ahorra memoria y decode, pero mete bandas en degradados.

### Fase 2 — Funciones centrales

**Aplicar fondo**
- Menú (bottom sheet) con "Pantalla de inicio", "Pantalla de bloqueo" y "Ambas"
  (`FLAG_SYSTEM`, `FLAG_LOCK` y la combinación).
- Se corrige el OOM: el bitmap ya no se decodifica entero. Se mide la imagen,
  se calcula `inSampleSize` y se decodifica solo la región necesaria.
- Recorte center-crop a la proporción de la pantalla: la imagen se escala al
  tamaño exacto, sin estirar ni bandas.
- Todo en `Dispatchers.IO`, con progreso, botón cancelar y mensajes claros.

**Descarga**
- Streaming directo a la fila de MediaStore: la imagen nunca se carga entera en
  memoria.
- Progreso visible con porcentaje y cancelación que borra la fila pendiente.
- Nombre único (`dannx-<id>-<timestamp>.<ext>`) y extensión real detectada por
  la firma de bytes del archivo, no solo por el `Content-Type`.
- Detección de duplicados: si el archivo sigue en el teléfono, se ofrece abrir
  la copia existente o descargarla de nuevo.
- Snackbar final con acción "Abrir" (abre la imagen con la app de galería).

**Zoom**
- Pellizco, arrastre y doble toque (1x ↔ 2.5x) con animación.
- Escala entre 1x y 5x, con límites de arrastre según el tamaño escalado para
  no perder la imagen de vista.
- Zoom y desplazamiento se guardan con `rememberSaveable`: sobreviven a la
  rotación.

**Compartir**
- `FileProvider` declarado en el manifest con `res/xml/file_paths.xml`.
- Se comparte la imagen desde `cache/shared` con `ClipData` y permiso temporal
  de lectura.
- Compartir ya no deja copia en la galería.
- Se limpian los archivos viejos de `cache/shared` (8 máximo o 6 h).

### Fase 1 — Auditoría

- Revisión completa del proyecto (código, recursos, build, CI y `setup.sql`).
- Informe de riesgos priorizado (crítico / importante / menor) con los puntos
  abiertos: firma de release, roles de administrador en Supabase, caché offline
  del feed, thumbnails en el backend, strings sin extraer y zoom/rotación.

### Rendimiento y pulido previos

- Grillas uniformes de 2 columnas en 9:16 (antes `LazyVerticalStaggeredGrid`,
  más cara de medir) en Explorar, Favoritos y detalle de colección.
- Pipeline de miniaturas determinista: `.size(540, 960)` en cards y prefetch,
  prefetch limitado a 12 con prioridad baja y arranque diferido 300 ms.
- `bitmapFactoryMaxParallelism(2)`, crossfade corto solo en hero.
- Ícono adaptativo nuevo con capa monocroma, splash screen y accesos directos.
- Firma de debug estable (`debug-signing.p12`) para que las actualizaciones
  instalen encima sin desinstalar.
- README profesional y textos de desarrollo fuera de la app.

### Verificación

- CI: build de debug y release (R8 con `minify` + `shrinkResources`) y tests
  unitarios en cada push a `main`.
- Tests nuevos: geometría de recorte y muestreo (`WallpaperGeometryTest`),
  límites de zoom (`ZoomGeometryTest`).

## Pendiente (siguientes fases)

- Fase 3: robustez (estados de carga/vacío/error, modo sin conexión,
  reintentos, backpressure de sesión, rotación y proceso muerto).
- Fase 4: rendimiento (paginación del feed, caché con límites, imágenes
  livianas en la grilla).
- Fase 5: experiencia de usuario y accesibilidad (strings.xml, "Acerca de",
  confirmaciones y deshacer).
- Fase 6: seguridad y privacidad (RLS solo-admin, tokens, `PRIVACY.md`).
- Fase 7: distribución por APK (keystore propio, tags, checksum, verificador de
  actualizaciones).
- Fase 8: pruebas finales (tests de ViewModel y repositorio, lista manual).

## Estado actual

- Última CI verde: run `39628290733` (commit `218d413`): compila debug y
  release (R8) y pasan los 39 tests unitarios.
- APKs para el teléfono en `Descargas/`: `DannX-debug.apk` (65 MB, con el
  contador de FPS) y `DannX-release.apk` (3.4 MB, el de uso diario).
- Guía para conectar el backend y publicar wallpapers: `CONECTAR-SUPABASE.md`.
- **Pendiente de prueba en dispositivo**: modo avión, proceso matado, token
  vencido, duplicados y el jank del scroll con las nuevas optimizaciones.

- Rama `main` en GitHub, CI verde en cada push (build debug + release con R8 y
  tests unitarios).
- Última CI: run `39535976641` (commit `7550243`). Commits de la Fase 2:
  `d1aec69` (aplicar fondo), `38cacc4` (descarga), `3cad7bf` (zoom), `f9bf48e`
  (compartir), `7550243` (fix de compilación), `5b09cac` (este changelog).
- APKs para instalar en el teléfono: `DannX-debug.apk` (65 MB) y
  `DannX-release.apk` (3.4 MB) en `Descargas/` del almacenamiento compartido.
- **Pendiente de prueba manual**: aplicar fondo en las tres pantallas, descarga
  con progreso/cancelación/duplicado, zoom con doble toque y rotación, y
  compartir la imagen. Nada de eso está verificado en un dispositivo real.
- **Pendiente de corrección conocido**: la copia de `cache/shared` que se
  comparte conserva extensión `.img`; apps muy estrictas podrían no inferir el
  tipo, aunque el intent usa `image/*`.
- El APK público sigue firmado con el keystore de debug: no distribuir todavía
  (Fase 7).
