# Base de datos

La fuente de verdad del esquema son los archivos de `docs/migraciones/`, en
orden alfabético. `setup.sql` es el esquema inicial histórico y no refleja los
cambios posteriores.

## Aplicar las migraciones

En el repositorio, el workflow **Apply Supabase migration** ejecuta los archivos
con la Management API. Requiere el secret `SUPABASE_ACCESS_TOKEN` y la variable
`SUPABASE_PROJECT_REF`.

```bash
# Una migración puntual
gh workflow run apply-migration.yml -f file=docs/migraciones/20261119_tag_integrity.sql

# Todas, en orden alfabético
gh workflow run apply-migration.yml
```

Cada ejecución termina con dos verificaciones: la estructura (tablas, políticas,
funciones y restricciones) y el comportamiento de las etiquetas
(`scripts/sql/verify_tags.sql`, que corre en una transacción descartada).

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
