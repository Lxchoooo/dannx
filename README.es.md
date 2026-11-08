# DannX

[English](README.md) | Español

[![Android CI](https://github.com/Lxchoooo/dannx/actions/workflows/android.yml/badge.svg)](https://github.com/Lxchoooo/dannx/actions/workflows/android.yml)
![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.09.00-4285F4?logo=jetpackcompose&logoColor=white)
![Material 3](https://img.shields.io/badge/Material%203-1.5.0--rc01-6750A4?logo=materialdesign&logoColor=white)
![Licencia: MIT](https://img.shields.io/badge/Licencia-MIT-yellow.svg)

Una app de fondos de pantalla para Android hecha en Kotlin con Jetpack Compose.
Explorá un feed curado, armá tus colecciones con los favoritos y aplicá o
descargá cualquier imagen en dos toques. Viene con un feed de ejemplo incluido,
así que funciona apenas la instalás.

## 📸 Capturas

| Explorar | Detalle |
| --- | --- |
| ![Explorar](docs/screenshots/explore.png) | ![Detalle](docs/screenshots/detail.png) |

| Colecciones | Favoritos |
| --- | --- |
| ![Colecciones](docs/screenshots/collections.png) | ![Favoritos](docs/screenshots/favorites.png) |

> Las capturas viven en [`docs/screenshots/`](docs/screenshots/). Fijate en la
> nota de ahí si todavía no están disponibles.

## ✨ Características

- Feed con destacados, búsqueda, categorías y tags libres.
- Colecciones que creás dentro de la app, públicas o privadas.
- Vista de detalle con zoom con dos dedos, doble toque y desplazamiento.
- Descarga a la galería (`Pictures/DannX`) usando el MediaStore del sistema.
- Aplicar en pantalla de inicio, pantalla de bloqueo o ambas, recortado solo.
- Compartir cualquier fondo con otras apps.
- Favoritos guardados en el teléfono.
- Ícono adaptativo monocromo que sigue el tema del sistema (Android 13+),
  splash screen y accesos directos en el launcher.
- Funciona sin conexión: el feed que ya cargaste sigue disponible.
- Temas claro y oscuro, con color dinámico en Android 12+.
- Pantalla de ajustes: tema (sistema, claro, oscuro), modo lite, borrar caché y
  acceso a la última release.
- Los botones del detalle toman el color del fondo que estás mirando.
- Pantallas de bienvenida que se muestran una sola vez al inicio.

## ⬇️ Descarga e instalación

1. Descargá el APK desde la página
   [Releases](https://github.com/Lxchoooo/dannx/releases): la versión
   **DannX · última versión** siempre tiene el build firmado más nuevo, y se
   regenera sola en cada merge a `main`. Si necesitás un run puntual, el
   artifact `DannX-release-<número>` también está en
   [Actions](https://github.com/Lxchoooo/dannx/actions).
2. Abrí el APK en el teléfono y aceptá instalar desde **orígenes desconocidos**
   cuando Android te lo pida.
3. Las actualizaciones se instalan encima de la app existente: se conservan
   favoritos, colecciones y ajustes.
4. Los artifacts de debug que CI compila con el secreto del keystore usan la
   misma clave de firma que los releases, así que también se instalan encima de
   la app publicada. Usá el artifact del run más reciente; un debug viejo no
   puede reemplazar un release más nuevo.

Requisitos: **Android 10 (API 29) o superior**. Los builds de release se firman
con una clave privada guardada como secreto de GitHub; la clave nunca se
commitea al repositorio.

## 🛠 Desarrollo

Requisitos: **JDK 17** y el Android SDK (el proyecto compila contra SDK 37,
apunta a SDK 36 y como mínimo soporta SDK 29).

```bash
./gradlew assembleDebug        # APK de debug
./gradlew lintDebug            # reporte de lint
./gradlew testDebugUnitTest    # tests unitarios
./gradlew assembleRelease      # APK de release firmado (necesita el keystore)
```

`assembleDebug` usa el keystore de release cuando `ANDROID_KEYSTORE_PATH` está
definido; CI lo configura para que los artifacts de debug se instalen encima del
release publicado. Sin keystore, Gradle usa la firma de depuración habitual.

Estructura del proyecto:

```
app/src/main/java/com/dannx/walls/
├── data/      # cliente de API, caché, preferencias, feed demo, reintentos
├── ui/        # pantallas, componentes, tema, clases de tamaño de ventana
└── util/      # archivos de fondo (galería, aplicar) y helpers de geometría
.github/       # workflows de CI
docs/          # capturas y assets de documentación
```

## 🗺 Hoja de ruta

Todo lo siguiente está **planeado** y todavía no existe:

- Panel web de administración para gestionar fondos.
- Perfil de base para mejorar el rendimiento en equipos de gama baja.
- Release firmado distribuido fuera de GitHub.
- Actualizador completo dentro de la app (por ahora, Ajustes abre la página de
  releases).

## 🐞 Contribuir

¿Encontraste un bug o tenés una idea? Abrí un
[issue](https://github.com/Lxchoooo/dannx/issues) con los detalles. Los pull
requests son bienvenidos.

## 📄 Licencia

Publicado bajo la [licencia MIT](LICENSE). Podés usar, copiar, modificar y
distribuir el código, manteniendo el aviso de autoría.

## 🎨 Créditos

Los fondos incluidos en el feed de ejemplo son obras originales y tienen su
propia autoría y condiciones de licencia. No forman parte de la licencia del
código.
