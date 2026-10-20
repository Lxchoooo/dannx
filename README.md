# DannX

App Android de wallpapers, estilo Zedge, con estética Material 3 Expressive.
Tu amigo publica fondos desde la pestaña **Estudio** y cualquier persona puede
verlos, descargarlos y aplicarlos como fondo de pantalla.

## Estado

- Feed con destacados, búsqueda, categorías y tags.
- Colecciones propias tipo carpeta/edición: Immortal Edition, Flowers, etc.
- Detalle con zoom, descargar, aplicar fondo, compartir y guardar en colección.
- Favoritos guardados en el teléfono.
- Panel de administrador con inicio de sesión y subida a Supabase.
- Modo demo con fondos y colecciones de ejemplo cuando Supabase no está conectado.
- El APK se compila en GitHub Actions, no en el teléfono.

## Estructura

```
app/src/main/java/com/dannx/walls/
  MainActivity.kt          Punto de entrada
  MainViewModel.kt         Estado de la app
  data/                    Modelos, demo, preferencias y API de Supabase
  ui/                      Tema, navegación, pantallas y componentes
  util/WallpaperFiles.kt   Descargar, aplicar fondo y compartir
supabase/setup.sql         Base de datos, permisos y bucket
.github/workflows/         Compilación del APK
```

## 1. Conectar Supabase (una sola vez)

1. Crea una cuenta y un proyecto gratis en https://supabase.com.
2. En el proyecto, abre **SQL Editor**, pega el contenido de
   `supabase/setup.sql` y ejecútalo. Si ya tenías el proyecto creado, vuelve
   a ejecutarlo: agrega tags y las tablas de colecciones sin borrar nada.
3. Ve a **Authentication > Users** y crea el usuario de tu amigo
   (correo y contraseña). Ese será el único administrador.
4. En **Authentication > Providers > Email**, desactiva
   "Allow new users to sign up" para que nadie más se registre.
5. En **Project Settings > API** copia:
   - `Project URL` (empieza con https://)
   - `anon public` key

La `anon key` es una clave pública: la seguridad real la dan las políticas
del archivo SQL. No compartas la `service_role key`.

## 2. Configurar GitHub

En el repositorio, ve a **Settings > Secrets and variables > Actions >
Variables** y crea:

| Variable | Valor |
| --- | --- |
| `SUPABASE_URL` | Tu Project URL |
| `SUPABASE_ANON_KEY` | Tu anon public key |

Cada vez que se suba un cambio a `main`, GitHub Actions compila y publica el
APK en la sección **Actions > último run > Artifacts** como `DannX-debug`.

Para lanzar una versión con enlace de descarga fijo, crea un tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

El APK queda adjunto en **Releases**.

## 3. Instalar la app

1. Descarga `app-debug.apk` desde Actions o Releases.
2. Ábrelo en el teléfono y acepta "instalar apps de origen desconocido".
3. Listo. Sin Supabase funciona con un feed de ejemplo; con Supabase carga
   los fondos reales.

El APK queda firmado siempre con la misma clave incluida en el repositorio:
las actualizaciones se instalan encima sin necesidad de desinstalar. Para
publicar en Play Store más adelante se recomienda mover la firma a un
keystore privado.

## 4. Colecciones y tags

La idea es combinar lo visual de Wallcraft con la libertad de Zedge:

- En **Colecciones** puedes crear carpetas con el nombre que quieras
  (`Immortal Edition`, `Flowers`, `Naruto`, etc.) y elegir si son públicas.
- Desde el detalle de cualquier fondo, el botón de colecciones permite
  guardarlo en una carpeta existente o crear una nueva al vuelo.
- Los tags son libres: se escriben separados por comas al publicar y se
  pueden buscar desde el feed. No hay moderación automática que borre un
  fondo por usar un tag.
- Sin Supabase configurado, las colecciones se guardan en el teléfono.

## 5. Cómo publica tu amigo

1. Abre la pestaña **Estudio**.
2. Entra con el correo y la contraseña creados en Supabase.
3. Elige la imagen, ponle título, categoría, tags y, si quiere, una colección.
4. Toca **Publicar wallpaper**. El fondo aparece al inicio del feed y en la
   colección elegida.

## Probar en la computadora

El wrapper de Gradle está incluido:

```bash
./gradlew assembleDebug
```

Los tests del filtro de búsqueda:

```bash
./gradlew testDebugUnitTest
```

## Límites de los planes gratis

- Supabase: 1 GB de archivos, 5 GB de tráfico al mes y el proyecto se pausa
  tras una semana sin uso (se reactiva desde el panel).
- GitHub Actions: minutos ilimitados en repositorios públicos y 2.000 min/mes
  en privados. Un build tarda unos 6 minutos.
