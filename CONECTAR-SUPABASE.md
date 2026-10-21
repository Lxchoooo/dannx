# Conectar Supabase para publicar tus wallpapers

La app ya tiene toda la parte de subida implementada (pantalla **Estudio**).
Lo que falta es encender el backend. Son 6 pasos, unos 15 minutos.

## 1. Crear el proyecto (gratis)

1. Entrá a <https://supabase.com/dashboard> y creá una cuenta.
2. **New project** → poné un nombre (por ejemplo `dannx`) → elegí una región
   cercana → **Create new project**. Te genera una contraseña de base de datos:
   **guardala**, no la vas a necesitar para la app, pero sí para el panel.

## 2. Crear las tablas y el bucket

1. En el proyecto, abrí **SQL Editor** (ícono `</>` en la barra lateral).
2. Abrí el archivo `supabase/setup.sql` del repositorio, copiá **todo** su
   contenido y pegalo en el editor.
3. **Run**. Tiene que salir "Success. No rows returned".

Esto crea las tablas `wallpapers`, `collections` y `collection_items`, activa las
políticas de seguridad (RLS) y crea el bucket público `wallpapers` donde se
guardan las imágenes.

## 3. Crear tu cuenta de administrador

1. **Authentication → Users → Add user → Create a new user**.
2. Poné tu correo, una contraseña y activá **Auto Confirm User**.
3. Guardá el usuario: esa es la cuenta con la que vas a entrar en la app.

## 4. Desactivar los registros abiertos

1. **Authentication → Providers → Email**.
2. Apagá **Allow new users to sign up** (toggle "Enable sign up").
3. Guardá.

Esto es importante: si queda abierto, cualquier persona que se registre podría
subir y borrar fondos. Con esto apagado, la única cuenta que existe es la tuya.

## 5. Copiar las dos claves

1. **Project Settings → API** (o *Settings → Data API* en la interfaz nueva).
2. Copiá dos valores:
   - **Project URL** → `https://xxxx.supabase.co`
   - **anon public** → la clave larga que empieza con `eyJ...`

> La clave **anon** es pública por diseño: la app la lleva dentro y las reglas
> de la base (RLS) son las que protegen los datos. **Nunca** copies la clave
> `service_role`: esa no va en la app ni en el repo.

## 6. Cargar las variables en GitHub

En el repositorio (`Lxchoooo/dannx`):

1. **Settings → Secrets and variables → Actions → Variables** (la pestaña
   *Variables*, no *Secrets*).
2. **New repository variable** → nombre `SUPABASE_URL`, valor = tu Project URL.
3. **New repository variable** → nombre `SUPABASE_ANON_KEY`, valor = la clave
   anon.
4. Guardá los dos.

Listo. Con esto la app deja de arrancar en modo demo: el feed pasa a mostrar lo
que esté publicado en `wallpapers` y la pestaña **Estudio** habilita la subida.

## 7. Recompilar la app

Las variables se inyectan al compilar, así que hace falta un build nuevo:

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
| La app sigue mostrando el feed de ejemplo | Faltan las variables en GitHub, o el APK se compiló antes de cargarlas |
| "Inicia sesión para crear colecciones" | No iniciaste sesión en Estudio, o el usuario no está confirmado |
| "Falta conectar Supabase en la configuración" | `SUPABASE_URL` quedó vacío o mal pegado |
| Al subir, error 401 | La clave anon está mal copiada, o el signup sigue abierto y la sesión venció |
| La imagen no aparece en la galería del teléfono | Es normal: las imágenes viven en el bucket de Supabase, no en el teléfono hasta que las descargás desde la app |

## Nota de seguridad (pendiente en Fase 6)

Hoy las políticas de escritura permiten a **cualquier** usuario autenticado
subir o borrar. Con el signup apagado, en la práctica solo existe tu cuenta, así
que es seguro en la práctica; pero la Fase 6 lo va a endurecer con un rol
`admin` real en el JWT, para que quede garantizado aunque alguien vuelva a
activar los registros.
