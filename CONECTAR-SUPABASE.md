# Conectar Supabase para publicar tus wallpapers

Ya tenés la pantalla **Estudio** dentro de la app: falta prenderle el backend.
Son 7 pasos y toma unos 15 minutos la primera vez.

## 1. Crear el proyecto (gratis)

1. Entrá a <https://supabase.com/dashboard> y creá una cuenta.
2. **New project** → poné un nombre (por ejemplo `dannx`) → elegí una región
   cercana → **Create new project**. Te genera una contraseña de base de datos:
   guardala. No la usa la app, pero sí la vas a necesitar si algún día querés
   tocar la base desde el panel de Supabase.

## 2. Crear tu cuenta de administrador

1. **Authentication → Users → Add user → Create a new user**.
2. Poné tu correo, una contraseña y activá **Auto Confirm User**.
3. Copiá el **User UID** de esa cuenta: es el identificador que va a quedar
   habilitado para publicar. Ninguna otra cuenta va a poder editar ni borrar
   fondos, así que este paso es el que define quién es el dueño del contenido.

## 3. Crear las tablas y el bucket

1. Abrí [`supabase/setup.sql`](supabase/setup.sql) del repositorio y copiá
   todo su contenido en el **SQL Editor** (ícono `</>` en la barra lateral).
2. El archivo ya trae un UUID de administrador: reemplazalo por el User UID
   del paso anterior. Solo hace falta volver a pegarlo si algún día recreás la
   cuenta de administrador.
3. **Run**. Tiene que salir "Success. No rows returned".

El script crea las tablas `wallpapers`, `collections` y `collection_items`,
activa las políticas de seguridad (RLS), limita las escrituras a tu UUID y
configura el bucket público `wallpapers` con un máximo de 25 MB y solo
imágenes JPEG, PNG o WebP.

## 4. Desactivar los registros abiertos

1. **Authentication → Providers → Email**.
2. Apagá **Allow new users to sign up** (toggle "Enable sign up").
3. Guardá.

Las políticas ya rechazan a cualquier cuenta que no seas vos. Apagar el
registro de nuevos usuarios evita que se creen cuentas de más y también cierra
la puerta a que alguien lea colecciones privadas desde la API.

## 5. Copiar las dos claves

1. **Project Settings → API** (o *Settings → Data API* en la interfaz nueva).
2. Copiá dos valores:
   - **Project URL** → `https://xxxx.supabase.co`
   - **anon public** → la clave larga que empieza con `eyJ...`

> La clave **anon** es pública por diseño: la app la lleva dentro y son las
> reglas de la base (RLS) las que protegen los datos. **Nunca** copies la clave
> `service_role`: esa no va en la app ni en el repo.

## 6. Cargar las claves en GitHub

En el repositorio (`Lxchoooo/dannx`):

1. **Settings → Secrets and variables → Actions → Secrets** (la pestaña
   *Secrets*, no *Variables*).
2. **New repository secret** → nombre `SUPABASE_URL`, valor = tu Project URL.
3. **New repository secret** → nombre `SUPABASE_ANON_KEY`, valor = la clave
   anon.
4. Guardá los dos.

Hasta acá la app sigue en modo demo. Lo que cambia recién cuando compilás de
nuevo con estas claves adentro es el feed: pasa a mostrar lo publicado en
`wallpapers`, y el **Estudio** deja de estar bloqueado.

## 7. Recompilar la app

Las claves se inyectan en el APK al compilar, así que el que tenés instalado
no las tiene. Subí el cambio y esperá el build:

```bash
git add . && git commit -m "chore(db): configuración de administración" && git push origin main
```

GitHub Actions compila y deja el APK nuevo en el run (artefacto
`DannX-release`). Bajalo, instalalo encima de la app actual y abrí el
**Estudio**:

1. Entrá con tu cuenta de administrador.
2. **Elegir imagen** → título, categoría, tags (opcional) → **Publicar
   wallpaper**.
3. La imagen sube al bucket, se registra en la tabla `wallpapers` y aparece en
   el feed de Explorar.
4. Si elegiste una colección, el fondo también queda dentro de ella.

## Probarlo sin pasar por GitHub (opcional)

Si querés probar en el teléfono sin esperar la CI, en tu PC con el repo
clonado:

```bash
cp local.properties.example local.properties
# editá local.properties con tus dos valores
./gradlew assembleDebug
```

`local.properties` está en `.gitignore`, así que las claves no se commitean por
accidente.

## Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| La app sigue mostrando el feed de ejemplo | Faltan los secrets en GitHub, o el APK se compiló antes de cargarlos |
| "Iniciá sesión en Estudio para crear colecciones" | No iniciaste sesión en Estudio, o el usuario no está confirmado |
| "Falta conectar Supabase en la configuración" | `SUPABASE_URL` quedó vacío o mal pegado |
| Al subir, error 401 | La clave anon está mal copiada, o el signup sigue abierto y la sesión venció |
| La imagen no aparece en la galería del teléfono | Es normal: las imágenes viven en el bucket de Supabase, no en el teléfono hasta que las descargás desde la app |

## Nota de seguridad

Las políticas de escritura de `wallpapers` y del bucket solo aceptan el UUID
de administrador configurado en `supabase/setup.sql`. Cualquier otra cuenta
autenticada recibe un error de política al intentar publicar, editar o borrar.
El bucket además limita el tamaño a 25 MB y acepta solo JPEG, PNG y WebP.

Si algún día recreás la cuenta de administrador (nuevo UUID), volvé a correr
`setup.sql` con el UUID nuevo. Mientras no lo hagas, las escrituras quedan
bloqueadas.
