# Base de datos

La fuente de verdad del esquema son los archivos de `docs/migraciones/`, en
orden alfabético. `setup.sql` es el esquema inicial histórico y no refleja los
cambios posteriores: una instalación nueva se hace **solo con las migraciones**.

## 1. Crear el proyecto

1. Crear un proyecto en [supabase.com](https://supabase.com); el plan gratuito
   alcanza.
2. En **Project Settings → API**, copiar `Project URL` y `anon public`.
   - La `anon` es pública por diseño: viaja dentro del APK.
   - La `service_role` no se usa ni en la app ni en el repositorio.
3. En **Project Settings → General**, copiar el *Project ref*: es el
   identificador que usa el workflow de migraciones.

## 2. Configurar el repositorio

| Nombre | Tipo | Para qué |
| --- | --- | --- |
| `SUPABASE_URL` | secret o variable | Compilar la app y el workflow de limpieza |
| `SUPABASE_ANON_KEY` | secret o variable | Compilar la app |
| `SUPABASE_ACCESS_TOKEN` | secret | Aplicar SQL con la Management API |
| `SUPABASE_PROJECT_REF` | variable | Elegir el proyecto en la Management API |
| `ANDROID_KEYSTORE_BASE64` | secret | Firmar el APK de release |
| `ANDROID_KEYSTORE_PASSWORD` | secret | Firmar el APK de release |
| `ANDROID_KEY_ALIAS` | secret | Firmar el APK de release |
| `ANDROID_KEY_PASSWORD` | secret | Firmar el APK de release |

Los dos primeros se leen como `secrets.X || vars.X`, así que sirven en
cualquiera de los dos lugares. Para builds locales van en `local.properties`
(ver [CONTRIBUTING.md](../CONTRIBUTING.md)).

## 3. Crear el administrador

La app no tiene registro público: el único usuario que escribe es el admin.

1. En **Authentication → Users**, agregar un usuario con email y contraseña
   (marcarlo como confirmado).
2. Copiar su UUID y registrarlo en la base:

   ```sql
   insert into public.admins (user_id)
   values ('00000000-0000-0000-0000-000000000000')
   on conflict (user_id) do nothing;
   ```

   La tabla la crea `20261108_estudio.sql`; si todavía no corriste las
   migraciones, este paso va después del 4.
3. En **Authentication → Providers → Email**, desactivar *Allow new users to
   sign up*. Sin eso, cualquiera puede crearse una cuenta (aunque seguiría sin
   poder escribir, porque no está en `public.admins`).

## 4. Aplicar las migraciones

El workflow **Apply Supabase migration** envía los archivos con la Management
API, en orden alfabético, y después verifica.

```bash
# Una migración puntual
gh workflow run apply-migration.yml -f file=docs/migraciones/20261120_storage_bucket.sql

# Todas, en orden alfabético
gh workflow run apply-migration.yml
```

También se pueden pegar a mano en el editor SQL del panel, uno por uno y en el
mismo orden. Todos los archivos son idempotentes: repetirlos no rompe nada.

## 5. Qué queda creado

- Tablas del catálogo, el Estudio, colecciones y la cola de limpieza, con RLS
  activo y políticas de lectura pública.
- El bucket `wallpapers` con límite de 25 MB y solo JPEG, PNG o WebP
  (`20261120_storage_bucket.sql`). Las políticas de Storage de las migraciones
  anteriores lo daban por existente.
- La purga diaria de la papelera con `pg_cron`, si la extensión está
  disponible. Si el proyecto no la tiene, el archivo avisa con un `notice` y la
  purga queda para cuando se habilite.
- Las RPC atómicas de acciones en bloque, búsqueda y normalización de etiquetas.
- Los límites de consulta: `statement_timeout` de 3 s para `anon` y 10 s para
  `authenticated`, más la extensión `pg_stat_statements` para medir
  (`20261121_query_guards.sql`). Las consultas de medición están en
  `pg_stat_measure.sql` y `pg_stat_measure_app.sql` y se corren con el workflow
  pasando la ruta del archivo.

## 6. Verificar

Cada ejecución del workflow termina con tres comprobaciones:

1. **Estructura**: columnas, RLS, funciones y restricciones esperadas.
2. **Etiquetas** (`scripts/sql/verify_tags.sql`): guarda un fondo real, comprueba
   la normalización y borra sus propios datos.
3. **Anónimo** (`scripts/sql/verify_anon.sql`): intenta escribir con el rol
   `anon`; cada intento tiene que fallar. Si alguno pasa, la sentencia levanta un
   error y la transacción se revierte.

Fuera del workflow:

```bash
# Confirmar el bucket y sus límites
select id, public, file_size_limit, allowed_mime_types
from storage.buckets
where id = 'wallpapers';

# Confirmar que hay un admin
select user_id, created_at from public.admins;
```

## 7. Probar desde la app

1. Compilar con `SUPABASE_URL` y `SUPABASE_ANON_KEY`.
2. En Ajustes, iniciar sesión con el usuario admin.
3. Subir una imagen desde el Estudio. Si falta el bucket, el error es
   `Bucket not found`; si falta el admin en la tabla, el error es de permisos
   (`new row violates row-level security policy`).

## Orden de las migraciones

| Archivo | Contenido |
| --- | --- |
| `20261108_estudio.sql` | Estudio de administración, categorías y colecciones |
| `20261109_app_editing.sql` | Edición de fondos y ajustes de la app |
| `20261109_fix_collections.sql` | Privacidad de colecciones |
| `20261110_studio_trash_bulk.sql` | Papelera, acciones en bloque y cola de limpieza |
| `20261111_tag_categories.sql` | Reglas etiqueta → categoría |
| `20261112_normalize_wallpaper_tags.sql` | Etiquetas separadas y filtro sin mayúsculas |
| `20261114_search_and_integrity.sql` | Búsqueda, índices y controles |
| `20261115_sql_fixes.sql` | Correcciones de reglas, purga y colecciones |
| `20261116_policies.sql` | Políticas de acceso |
| `20261117_sql_maintenance.sql` | Mantenimiento y auditoría |
| `20261118_tag_casing.sql` | Escritura única de etiquetas |
| `20261119_tag_integrity.sql` | Coherencia de reglas, auditoría y limpieza |
| `20261120_storage_bucket.sql` | Bucket `wallpapers` para instalaciones nuevas |
| `20261121_query_guards.sql` | `statement_timeout` y `pg_stat_statements` |
