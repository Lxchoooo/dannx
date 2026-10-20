# DannX

[![Android CI](https://github.com/Lxchoooo/dannx/actions/workflows/android.yml/badge.svg)](https://github.com/Lxchoooo/dannx/actions/workflows/android.yml)

Wallpaper app para Android siguiendo Material 3: feed con destacados,
colecciones propias, tags libres y un panel de administrador para publicar.
Compilada y distribuida con GitHub Actions.

## Características

- Feed con destacados, búsqueda, categorías y tags.
- Colecciones tipo carpeta, públicas o privadas, creadas dentro de la app.
- Detalle con zoom, descarga, aplicar como fondo de pantalla y compartir.
- Favoritos guardados en el teléfono.
- Ícono adaptativo monocromo que sigue el tema del sistema (Android 13+),
  splash screen y accesos directos en el launcher.
- APK automático en cada cambio a `main`.

## Instalación

Descargá el APK desde [Actions](https://github.com/Lxchoooo/dannx/actions)
(artefacto `DannX-debug` del último run) o desde
[Releases](https://github.com/Lxchoooo/dannx/releases) para versiones
etiquetadas. Abre el archivo y acepta instalar desde origen desconocido.

Las actualizaciones se instalan encima sin desinstalar: todos los builds se
firma con la misma clave.

## Back end (opcional)

Sin configurar, la app arranca con un feed de ejemplo y guarda favoritos y
colecciones en el teléfono. Para un feed real:

1. Ejecutá `supabase/setup.sql` en el SQL Editor de un proyecto Supabase.
2. Definí las variables `SUPABASE_URL` y `SUPABASE_ANON_KEY` en
   **Settings > Secrets and variables > Actions > Variables**.

La guía completa paso a paso está como comentario al inicio del propio
`supabase/setup.sql`.

## Desarrollo

La compilación requiere JDK 17 y el SDK de Android 36:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

### Estructura

```
app/src/main/java/com/dannx/walls/
  MainActivity.kt          Punto de entrada
  MainViewModel.kt         Estado de la app
  data/                    Modelos, demo, preferencias y API de Supabase
  ui/                      Tema, navegación, pantallas y componentes
  util/WallpaperFiles.kt   Descargar, aplicar fondo y compartir
supabase/setup.sql         Base de datos, permisos y bucket
.github/workflows/         Compilación y publicación del APK
```
