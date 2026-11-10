# DannX

[English](README.md) | Español

[![Android CI](https://github.com/Lxchoooo/dannx/actions/workflows/android.yml/badge.svg)](https://github.com/Lxchoooo/dannx/actions/workflows/android.yml)
[![CodeQL](https://github.com/Lxchoooo/dannx/actions/workflows/codeql.yml/badge.svg)](https://github.com/Lxchoooo/dannx/actions/workflows/codeql.yml)
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

Las capturas del build actual viven en
[`docs/screenshots/`](docs/screenshots/). El set de 1.5.0 se está renovando para
1.6.0; mirá la nota de ahí para saber cómo tomarlas.

## 🎬 Demo

Próximamente: GIF de demo. Va a quedar en
[`docs/demo.gif`](docs/demo.gif) y se muestra acá cuando esté subido.

## ✨ Características

- Feed con destacados, búsqueda, categorías y tags libres.
- Carga de a 30 fondos con búsqueda y filtros en el servidor; la caché offline
  guarda hasta 400.
- Actualizador dentro de la app, con descarga e instalación desde Ajustes.
- **Sorprendeme** abre un fondo aleatorio del filtro actual.
- Colecciones que creás dentro de la app, públicas o privadas.
- Vista de detalle con zoom con dos dedos, doble toque y desplazamiento.
- Editor integrado con encuadre, ajustes de color, filtros y desenfoque
  progresivo real.
- Descarga a la galería (`Pictures/DannX`) usando el MediaStore del sistema.
- Aplicar en pantalla de inicio, pantalla de bloqueo o ambas, recortado solo.
- Compartir cualquier fondo con otras apps.
- Favoritos guardados en el teléfono.
- Interfaz en español e inglés.
- Ícono adaptativo monocromo que sigue el tema del sistema (Android 13+),
  splash screen y accesos directos en el launcher.
- Funciona sin conexión: el feed que ya cargaste sigue disponible.
- Temas claro y oscuro, con color dinámico en Android 12+.
- Pantalla de ajustes: tema (sistema, claro, oscuro), modo lite, borrar caché,
  una sección Acerca de con la política de privacidad y acceso a las versiones
  numeradas.
- Los botones del detalle toman el color del fondo que estás mirando.
- Pantallas de bienvenida que se muestran una sola vez al inicio.

## ⬇️ Descarga e instalación

1. Descargá el APK desde la página
   [Releases](https://github.com/Lxchoooo/dannx/releases). Abrí la versión
   numerada más nueva (por ejemplo, `v1.6.0`) y bajá `DannX.apk`. Las versiones
   numeradas anteriores siguen disponibles. Si necesitás un run puntual de CI,
   el artifact `DannX-release-<número>` también está en
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

Mirá [CONTRIBUTING.es.md](CONTRIBUTING.es.md) para el setup completo, el estilo
de código y la checklist de pull requests.

## 🏗 Arquitectura

```mermaid
flowchart LR
    UI["UI en Compose\nfeed · detalle · editor · estudio"] --> VM["MainViewModel\nun solo UiState"]
    VM --> Catalog["Cliente del catálogo\nlectura, búsqueda, contenido propio"]
    VM --> Store["Almacenamiento del teléfono"]
    Catalog --> Remote["Catálogo remoto\ny entrega de imágenes"]
    Store --> Disk[("Disco del teléfono")]
```

- **UI**: pantallas y componentes en Compose. Solo leen `UiState` y devuelven
  eventos; ninguna pantalla habla con la red por su cuenta.
- **ViewModel**: mantiene el único `UiState`, combina filtros, paginación y la
  tarjeta del día, y decide cuándo refrescar, cachear o caer al feed demo.
- **Cliente del catálogo**: lecturas paginadas (30 por página), búsqueda y
  filtros resueltos del lado remoto, más las acciones de contenido propio que
  usa Estudio.
- **Almacenamiento del teléfono**: favoritos, colecciones, búsquedas recientes,
  ajustes y la caché del feed (hasta 400 fondos) para abrir con contenido sin
  conexión.
- **Capa de imágenes**: Coil pide cada imagen al tamaño que se dibuja. El modo
  lite baja la resolución, no precarga y quita las transiciones.
- **Actualizaciones**: la comprobación y la descarga de versiones usan el mismo
  cliente; el instalador se abre por el sistema.

## ♿ Accesibilidad

- TalkBack anuncia cada tarjeta como una sola frase (título, categoría, autor)
  y los controles exponen su estado, por ejemplo favorito activado o no.
- Cada objetivo táctil mide al menos 48 dp, incluidos los botones de favorito y
  compartir sobre las imágenes.
- El texto escala con el tamaño de fuente del sistema; las etiquetas se
  acomodan en varias líneas en vez de recortarse.
- Los temas claro y oscuro están ajustados para el contraste, y el color
  dinámico sigue la paleta del sistema en Android 12+.
- El modo lite también reduce el movimiento: transiciones más cortas o
  inexistentes, sin escala al presionar y sin fundido entre imágenes.
- Las imágenes decorativas quedan fuera del árbol de accesibilidad; las que
  aportan información tienen descripción. Por ahora la verificación es manual
  con TalkBack y fuentes grandes — las pruebas instrumentadas están en la hoja
  de ruta.

## 🗺 Hoja de ruta

Todo lo siguiente está **planeado** y todavía no existe:

- Panel web de administración para gestionar fondos.
- Perfil de base para mejorar el rendimiento en equipos de gama baja.
- Release firmado distribuido fuera de GitHub.

Mirá [ROADMAP.es.md](ROADMAP.es.md) para la lista completa de prioridades y lo
que ya está hecho.

## 🐞 Contribuir

¿Encontraste un bug o tenés una idea? Abrí un
[issue](https://github.com/Lxchoooo/dannx/issues) con los detalles. Los pull
requests son bienvenidos.

Leé [CONTRIBUTING.es.md](CONTRIBUTING.es.md) antes de mandar cambios. Al
participar aceptás el [Código de Conducta](CODE_OF_CONDUCT.es.md).

## 🔒 Privacidad

DannX no incluye analítica, publicidad ni SDKs de seguimiento. Los favoritos, las
colecciones, los ajustes y la caché offline quedan en tu dispositivo. Mirá
[PRIVACY.es.md](PRIVACY.es.md) para el detalle.

## 🛡 Seguridad

No abras un issue público para vulnerabilidades. Seguí
[SECURITY.es.md](SECURITY.es.md) para reportarlas de forma privada.

Cada release incluye `DannX.apk.sha256` y una atestación de procedencia. Después
de descargarlo, verificá el checksum con `sha256sum -c DannX.apk.sha256` o la
atestación con `gh attestation verify DannX.apk --repo Lxchoooo/dannx`.

## 📄 Licencia

Publicado bajo la [licencia MIT](LICENSE). Podés usar, copiar, modificar y
distribuir el código, manteniendo el aviso de autoría.

## 🎨 Créditos

Los fondos incluidos en el feed de ejemplo son obras originales y tienen su
propia autoría y condiciones de licencia. No forman parte de la licencia del
código.
