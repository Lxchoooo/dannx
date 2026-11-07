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

Descargá el APK desde [Releases](https://github.com/Lxchoooo/dannx/releases)
para versiones etiquetadas, o desde [Actions](https://github.com/Lxchoooo/dannx/actions)
(artefacto `DannX-release` del último run). Abre el archivo y acepta instalar
desde origen desconocido.

Los builds de release se firman con una clave privada guardada como secreto de
GitHub, no en el repositorio. Las actualizaciones se instalan encima sin
desinstalar. Si venís de una versión firmada con la clave de debug anterior,
la primera actualización requiere desinstalar e instalar de nuevo.

## Back end (opcional)

Sin configurar, la app arranca con un feed de ejemplo y guarda favoritos y
colecciones en el teléfono. Para un feed real:

1. Ejecutá `supabase/setup.sql` en el SQL Editor de un proyecto Supabase,
   reemplazando antes el UUID de administrador.
2. Definí los secrets `SUPABASE_URL` y `SUPABASE_ANON_KEY` en
   **Settings > Secrets and variables > Actions > Secrets**.

La guía completa paso a paso está en `CONECTAR-SUPABASE.md`.

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
