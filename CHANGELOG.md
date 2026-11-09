# Changelog

Registro de los cambios hechos en DannX. Se actualiza al cerrar cada bloque de
trabajo (fases de la hoja de ruta) y al publicar cada versión.

Sobre el Estudio: la pantalla de publicación es de uso privado. El acceso está
reservado a administradores y no se detalla en la documentación.

## Sin publicar

### Traducción y editor

- La interfaz completa pasa a `strings.xml` y se traduce al inglés con
  `values-en`; no quedan textos hardcodeados en las pantallas.
- `StringResolver` permite resolver cadenas desde los ViewModels y desde capas
  que no son de Compose.
- El editor suma desenfoque progresivo real: tres niveles premoldeados, mezcla
  entre niveles adyacentes y render final sobre una copia de 512 px.
- El desenfoque se calcula una sola vez por nivel; mover el control no vuelve a
  difuminar la imagen.
- Tests nuevos de `EditorBlur` para uniformidad, varianza, alfa y mezcla.

### Documentación y comunidad

- `CONTRIBUTING.md` y `CONTRIBUTING.es.md` con setup, estilo, tests y checklist
  de pull requests.
- `CODE_OF_CONDUCT.md` y `CODE_OF_CONDUCT.es.md` con Contributor Covenant 2.1.
- `SECURITY.md` y `SECURITY.es.md` con el canal privado y las prácticas de
  seguridad del proyecto.
- `docs/designs/security-hardening.md` con la checklist de settings de GitHub.
- `ROADMAP.md` y `ROADMAP.es.md` con prioridades, estado y cosas que no se van a
  hacer.
- `PRIVACY.md` y `PRIVACY.es.md`, enlazados desde el README y desde Ajustes.
- El README deja de mostrar capturas inexistentes; se actualizan con el build
  1.4.0.
- Issue templates al día: versión 1.4.0, apartados Editor y Privacidad, y enlace
  de contribución corregido.

### Seguridad

- Se agregó CodeQL para Kotlin (`java-kotlin`, build manual) en `main`, en pull
  requests que toquen la app y en un análisis semanal.
- Dependency review bloquea pull requests con dependencias de severidad alta.
- CI valida el `gradle-wrapper.jar` con `gradle/actions/wrapper-validation`.
- Los releases publican `DannX.apk.sha256` junto al APK para verificar la
  descarga.
- Los releases generan una atestación de procedencia (SLSA) para verificar que
  el APK salió de este repositorio y de este workflow.
- Se reparó la columna de privacidad de `collections`, que tenía un nombre
  corrupto: ahora es `is_public`. Crear y editar colecciones vuelve a funcionar
  y la privacidad se respeta en el servidor.
- El DTO de colecciones asume privada por defecto si el servidor no manda el
  campo (antes asumía pública).
- El registro público de Supabase quedó deshabilitado.
- La verificación de migraciones ahora exige `is_public`, RLS activo en
  colecciones e ítems y políticas que usen esa columna.
- Se eliminó la deploy key con escritura; `main` exige los checks `checks` y
  `debug`; las actions están fijadas a SHA y el repo exige SHA pinning.
- Se restauró `supabase/setup.sql` como referencia histórica del esquema.
- El APK de release ya no incluye logs de depuración.
- Los tags publican `versionName` sin la `v` inicial.
- Las colecciones nuevas nacen privadas y el diálogo muestra la etiqueta
  correcta según el interruptor.

### Mejoras

- Ajustes suma Acerca de (versión, enlaces y licencias de terceros) y un acceso
  directo a la política de privacidad.
- En Android 13 o superior, Ajustes incluye “Idioma” y abre el selector de
  idioma por app para cambiar entre español e inglés sin tocar el teléfono.
- Acerca de suma “Compartir DannX” con el enlace del repositorio.
- La búsqueda del feed muestra un botón para borrar el texto cuando hay una
  consulta activa.
- La ficha del fondo suma botones laterales para pasar al fondo anterior o
  siguiente sin volver al feed.
- La caché del feed queda acotada a 400 fondos y 100 colecciones para evitar que
  crezca sin límite.
- CI publica el `mapping.txt` de R8 como artifact de 30 días para poder leer
  crashes ofuscados.
- Las notas de release se generan desde la sección “Sin publicar” del CHANGELOG,
  con un texto de respaldo si la sección está vacía.
- Tests nuevos de `FeedCache` (tope y archivo corrupto) y `StringResolver`
  (argumentos y cadenas sin argumentos).
- Gradle actualizado a 9.9.0; se elimina el aviso de versión obsoleta del CI.
- El job `debug` corre solo en pull requests: en `main` alcanza con la release
  rolling, así que se ahorra un runner por push.
- Ajustes incluye “Buscar actualizaciones”, que abre la última release.
- Dependabot agrupa las actualizaciones menores de AndroidX, Kotlin y las
  herramientas de build.
- El editor marca su estado y sus filtros como inmutables para reducir
  recomposiciones.
- El `versionName` de los builds locales pasa a 1.4.0.
- Actualizados `core-splashscreen` 1.2.0, `kotlinx-coroutines-test` 1.11.0 y
  las actions `setup-java` 6.2.0 y `download-artifact` 8.0.2.
- Se limpiaron los avisos de lint: extensiones KTX en preferencias, URIs y
  bitmaps, tamaños de ventana en Compose y una llamada a `super` innecesaria.
- La pantalla de detalle muestra la ayuda “puede tardar un momento” mientras
  aplica un fondo.

## v1.3.0

### Fase 5 — Experiencia

- **Librerías actualizadas**: Compose BOM 2026.09.00 (Compose 1.11), Material 3
  1.5.0-rc01 para el kit Expressive oficial, core 1.19.1, activity 1.13.0,
  lifecycle 2.11.0, navigation 2.10.2 y graphics-shapes 1.1.0 (las formas
  "cookie" expresivas).
- El tema completo usa `MotionScheme.expressive()`, y el onboarding muestra
  formas de marca (cookie 12 lados) como fondo decorativo.

- El onboarding de bienvenida hace su primera pasada: tres pantallas a página
  completa con pager horizontal, tipografía display y formas de acento, en el
  estilo Expressive de Material 3 (springs elásticos, formas decorativas e
  indicadores animados). Se muestra una sola vez y se salta con "Saltar".
- La pestaña **Estudio** ya no forma parte de la barra para el público: solo
  aparece cuando hay una sesión de administrador iniciada. El ingreso al área
  privada no se detalla en la documentación.
- El Estudio sin sesión muestra un botón "Volver" y se ve como una pantalla
  independiente, sin barra inferior.
- El feed se adapta al tamaño de pantalla: 2 columnas en celular, 3 en tablet
  chica y 4 en tablet grande o pantalla dividida; el hero se limita a poco más
  de la mitad del alto disponible para que no tape el resto.
- Accesibilidad: los botones de favorito y de quitar de una colección miden al
  menos 48 dp, TalkBack lee cada tarjeta como una sola frase, las flechas
  "Volver" se espejan en idiomas de derecha a izquierda, y las imágenes
  muestran un indicador al cargar y un aviso si no se pudieron descargar.
- Se eliminó código sin uso (`RoundedCornersTransformation`).

### Correcciones

- Las operaciones de etiquetas, el renombrado de categorías, el borrado con
  reasignación y el reordenamiento de colecciones usan una función atómica de
  la base de datos cuando la migración del Estudio está aplicada. Si la función
  todavía no existe, la app conserva el recorrido anterior para no romper la
  compatibilidad.
- Los APK de debug de CI se firman con la misma clave que release cuando el
  secreto está disponible, y usan el mismo `versionCode` del run para poder
  instalarse encima sin desinstalar.

### Fase 2 — Estabilidad

- Al volver a la app o tocar "Actualizar", el feed anterior sigue visible: ya no
  se reemplaza por el esqueleto de carga. Solo aparece una barra fina de
  progreso mientras se refresca en segundo plano.
- Si falla la carga de colecciones pero el feed responde, se muestra un aviso
  puntual sin ocultar el contenido.
- Un 403 (permiso denegado) ahora tiene su propio mensaje en vez de
  confundirse con una sesión vencida. Corregido el mensaje del error de
  servidor, que mezclaba inglés y español.
- Los errores de parseo ya no se reintentan: son deterministas y reintentarlos
  solo demoraba el aviso al usuario.
- El caché del feed se escribe de forma atómica (archivo temporal + reemplazo),
  así un cierre a mitad de escritura no lo deja corrupto.
- La imagen del detalle se pide al tamaño de la pantalla en vez de a resolución
  completa, para bajar el uso de memoria y evitar cierres por OOM.

### Fase 1 — Seguridad

**Permisos del backend**
- Las escrituras de `wallpapers` y del bucket ahora comparan `auth.uid()` con
  el UUID de administrador configurado en `supabase/setup.sql`. Cualquier otra
  cuenta autenticada recibe un error de política.
- El bucket `wallpapers` limita los archivos a 25 MB y solo acepta JPEG, PNG y
  WebP.
- Se retiró la función `increment_downloads`: cualquiera con la clave anon
  podía inflar el contador sin límite.
- `wallpapers` gana límites de longitud para título, categoría, autor y tags,
  columnas `updated_at` y `created_by`, e índices por fecha y tags.
- Las colecciones ahora permiten actualizar `position` dentro de la colección
  propia.

**Firma y distribución**
- Se eliminó `debug-signing.p12` del repositorio. El build de release usa una
  clave nueva guardada como secreto de GitHub, y los tags publican ese APK en
  lugar del build de debug.
- `versionCode` y `versionName` se derivan de propiedades de Gradle; CI usa el
  número de ejecución y el tag.
- El pipeline se separó en `verify` (lint + tests en cada PR) y `package`
  (APKs firmados en `main` y tags).

**Dispositivo**
- Se agregó el permiso `ACCESS_NETWORK_STATE`, que `Connectivity` ya leía.
- Los tokens de sesión viven en un archivo de preferencias separado, excluido
  de los backups en la nube y de la transferencia entre dispositivos.
- `Gradle` ya no recurre a la clave de debug para firmar release.

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
  instalen encima sin desinstalar. Reemplazada en la Fase 1 por una clave de
  release guardada fuera del repositorio.
- README profesional y textos de desarrollo fuera de la app.

### Verificación

- CI: build de debug y release (R8 con `minify` + `shrinkResources`) y tests
  unitarios en cada push a `main`.
- Tests nuevos: geometría de recorte y muestreo (`WallpaperGeometryTest`),
  límites de zoom (`ZoomGeometryTest`).

## Pendiente

- Capturas de 1.4.0 para `docs/screenshots/` (explorar, detalle, colecciones y
  favoritos).
- Probar en un dispositivo el desenfoque progresivo, el cambio de idioma, el
  modo avión, el proceso muerto y la actualización sobre la versión publicada.
- Revisar la rama `feat/1.4.0` y, cuando esté aprobada, abrir el PR y publicar
  el tag `v1.4.0`.

## Estado actual

- La actualización 1.4.0 está en la rama `feat/1.4.0`: traducción al inglés,
  desenfoque progresivo, documentación de comunidad, CodeQL, dependency review,
  validación del wrapper, checksums y pantalla Acerca de.
- La compilación y los tests se validan en GitHub Actions; en el teléfono no hay
  JDK ni Android SDK para correr Gradle.
- Tests unitarios nuevos: `EditorBlurTest`, `FeedCacheTest` y
  `StringResolverTest`.
- El APK de release se firma en CI; el keystore no está en el repositorio.
