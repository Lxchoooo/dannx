# Conectar Supabase para publicar tus wallpapers

La app ya tiene toda la parte de subida implementada (pantalla **Estudio**).
Lo que falta es encender el backend. Son 6 pasos, unos 15 minutos.

## 1. Crear el proyecto (gratis)

1. Entrá a <https://supabase.com/dashboard> y creá una cuenta.
2. **New project** → poné un nombre (por ejemplo `dannx`) → elegí una región
   cercana → **Create new project**. Te genera una contraseña de base de datos:
   **guardala**, no la vas a necesitar para la app, pero sí para el panel.

## 2. Crear tu cuenta de administrador

1. **Authentication → Users → Add user → Create a new user**.
2. Poné tu correo, una contraseña y activá **Auto Confirm User**.
3. Copiá el **User UID** de esa cuenta: lo vas a usar en el paso siguiente.
   Esa es la única cuenta que va a poder publicar, editar o borrar fondos.

## 3. Crear las tablas y el bucket

1. Abrí el archivo `supabase/setup.sql` del repositorio y copiá todo su
   contenido en el **SQL Editor** (ícono `</>` en la barra lateral).
2. En el editor, reemplazá **todas** las apariciones de
   `00000000-0000-0000-0000-000000000000` por el UUID del paso 2.
   Hasta que lo hagas, la base rechaza toda escritura, incluida la tuya.
3. **Run**. Tiene que salir "Success. No rows returned".

Esto crea las tablas `wallpapers`, `collections` y `collection_items`, activa
las políticas de seguridad (RLS), limita las escrituras a tu UUID y crea el
bucket público `wallpapers` con un máximo de 25 MB y solo imágenes
JPEG, PNG o WebP.

## 4. Desactivar los registros abiertos

1. **Authentication → Providers → Email**.
2. Apagá **Allow new users to sign up** (toggle "Enable sign up").
3. Guardá.

Las políticas ya bloquean a cualquier cuenta que no seas vos, pero dejar los
registros apagados evita que se creen cuentas que puedan leer colecciones
privadas.

## 5. Copiar las dos claves

1. **Project Settings → API** (o *Settings → Data API* en la interfaz nueva).
2. Copiá dos valores:
   - **Project URL** → `https://xxxx.supabase.co`
   - **anon public** → la clave larga que empieza con `eyJ...`

> La clave **anon** es pública por diseño: la app la lleva dentro y las reglas
> de la base (RLS) son las que protegen los datos. **Nunca** copies la clave
> `service_role`: esa no va en la app ni en el repo.

## 6. Cargar las claves en GitHub

En el repositorio (`Lxchoooo/dannx`):

1. **Settings → Secrets and variables → Actions → Secrets** (la pestaña
   *Secrets*, no *Variables*).
2. **New repository secret** → nombre `SUPABASE_URL`, valor = tu Project URL.
3. **New repository secret** → nombre `SUPABASE_ANON_KEY`, valor = la clave
   anon.
4. Guardá los dos.

Listo. Con esto la app deja de arrancar en modo demo: el feed pasa a mostrar lo
que esté publicado en `wallpapers` y la pestaña **Estudio** habilita la subida.

## 7. Recompilar la app

Las claves se inyectan al compilar, así que hace falta un build nuevo:

-(make push to main → la CI compila y sube el artifact)*

Después instalás el APK nuevo y:

1. Abrí la pestaña **Estudio**.
2. Entrá con tu cuenta de administrador.
3. **Elegir imagen** → título, categoría, tags (opcional) → **Publicar wallpaper**.
4. La imagen sube al bucket, se registra en la tabla `wallpapers` y aparece en
   el feed de Explorar.
5. Si elegiste una colección, el fondo también queda dentro de ella.

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
| "Inicia sesión para crear colecciones" | No iniciaste sesión en Estudio, o el usuario no está confirmado |
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
