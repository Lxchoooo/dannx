# Changelog

Registro de los cambios hechos en DannX. Se actualiza al cerrar cada bloque de
trabajo (fases de la hoja de ruta). La app todavía no tiene versiones públicas,
así que todo vive bajo "Sin publicar" hasta el primer tag.

## Sin publicar

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
