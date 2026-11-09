# Contribuir a DannX

Gracias por tomarte el tiempo de contribuir. DannX es una app Android chica y de
código abierto, así que los reportes claros y las pull requests enfocadas ayudan
mucho.

Al participar, aceptás seguir nuestro [Código de Conducta](CODE_OF_CONDUCT.md).

## Antes de empezar

- Para bugs o ideas de funciones, abrí un issue primero.
- Para problemas de seguridad, **no** abras un issue público: seguí
  [SECURITY.md](SECURITY.md).
- Mantené las pull requests chicas y enfocadas. Un tema por PR es más fácil de
  revisar y más rápido de validar en CI.

## Requisitos

- JDK 17.
- Android SDK 37. La app apunta a SDK 36 y soporta Android 10 (API 29) o superior.
- Usá el wrapper de Gradle que está en el repo (`./gradlew`), no una instalación
  local de Gradle.

La app funciona con el feed de ejemplo incluido. Para apuntarla a un proyecto de
Supabase, creá `local.properties` (no se commitea) o exportá las variables:

```properties
SUPABASE_URL=https://tu-proyecto.supabase.co
SUPABASE_ANON_KEY=tu-anon-key
```

`SUPABASE_ANON_KEY` es pública por diseño. Nunca pongas una service-role key en la
app ni en el repositorio.

## Compilar y probar

```bash
./gradlew assembleDebug        # APK de debug
./gradlew lintDebug            # reporte de lint
./gradlew testDebugUnitTest    # tests unitarios en la JVM
./gradlew assembleRelease      # APK de release firmado (necesita el keystore)
```

`assembleDebug` usa el keystore de release cuando `ANDROID_KEYSTORE_PATH` está
definido; CI lo configura para que los artifacts de debug se instalen encima del
release publicado. Sin keystore, Gradle usa la firma de depuración habitual.

## Estructura del proyecto

```
app/src/main/java/com/dannx/walls/
├── data/      # cliente de API, caché, preferencias, feed demo, reintentos
├── ui/        # pantallas, componentes, tema, clases de tamaño de ventana
└── util/      # archivos de fondo, descarga/aplicar, geometría
.github/       # workflows de CI y automatización del repositorio
docs/          # capturas, diseños y migraciones de la base
```

## Estilo de código

- Kotlin con indentación de 4 espacios, comas finales y sin imports con
  wildcard. Seguí el `.editorconfig` existente.
- Mantené los composables sin estado cuando se pueda: pasá estado y callbacks.
- Todo el texto visible para el usuario va en
  `app/src/main/res/values/strings.xml` y
  `app/src/main/res/values-en/strings.xml`. Usá `stringResource` en Compose y
  `StringResolver` fuera de Compose. No hardcodees textos de la UI.
- Marcá como inmutables el estado de UI y los modelos que usa Compose.
- Agregá tests de JVM para la lógica pura. `EditorBlurTest` es un buen ejemplo.
- Evitá dependencias nuevas salvo que sean necesarias; explicá el motivo en la PR.

## Tests

- Los tests unitarios viven en `app/src/test/` y corren en la JVM.
- Agregá un test de regresión para cada bug que se pueda probar sin dispositivo.
- Los tests instrumentados y los cambios que necesitan emulador van con una nota
  en la PR, porque CI no los ejecuta.

## Commits y pull requests

- Partí de `main`; no commitees directo ahí.
- Usá mensajes de commit claros. Español o inglés, los dos están bien.
- Completá la plantilla de la PR: qué cambió, por qué y cómo lo probaste.
- Los cambios de UI deberían incluir una captura o un video corto.
- `checks` (lint + tests unitarios) y `debug` tienen que estar verdes antes de
  mergear.
- Nunca commitees secretos, keystores, tokens, `local.properties` ni artefactos
  de build.

## Traducciones

- Agregá cada clave nueva en `values/strings.xml` y `values-en/strings.xml`.
- Usá `<plurals>` cuando el texto dependa de un número.
- Los datos de ejemplo y el overlay de FPS de debug no se traducen.

## Releases

Los releases los publica el maintainer:

- Un tag `vX.Y.Z` crea un release firmado y versionado.
- Cada merge a `main` actualiza el release rolling `DannX · última versión`.
- Las notas y el checksum del APK los genera CI.

## Licencia

DannX se distribuye bajo la [licencia MIT](LICENSE). Al contribuir, aceptás que
tus cambios se distribuyan bajo la misma licencia.
