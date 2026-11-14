-- Búsqueda más rápida y datos más prolijos.
-- Es idempotente: se puede ejecutar completo en el editor SQL de Supabase.

create schema if not exists extensions;
create extension if not exists unaccent with schema extensions;
create extension if not exists pg_trgm with schema extensions;

-- La búsqueda de la app resuelve nombres sin acentos ni mayúsculas.
set search_path = public, extensions, pg_temp;

-- Une título, autor, categoría y etiquetas en un solo texto de búsqueda.
-- Se guarda ya normalizado para que el índice no dependa de cómo se escribió.
create or replace function public.wallpaper_search_text(
    p_title text,
    p_author text,
    p_category text,
    p_tags text[]
)
returns text
language sql
immutable
parallel safe
set search_path = public, extensions, pg_temp
as $$
    select lower(
        unaccent(
            coalesce(p_title, '') || ' ' ||
            coalesce(p_author, '') || ' ' ||
            coalesce(p_category, '') || ' ' ||
            coalesce(array_to_string(p_tags, ' '), '')
        )
    );
$$;

revoke all on function public.wallpaper_search_text(text, text, text, text[]) from public;
grant execute on function public.wallpaper_search_text(text, text, text, text[]) to anon, authenticated;

alter table public.wallpapers
    add column if not exists search_text text
    generated always as (
        public.wallpaper_search_text(title, author, category, tags)
    ) stored;

-- Índice de texto para el buscador. Se busca la clase del operador en el
-- esquema donde haya quedado instalada pg_trgm.
do $$
declare
    op_namespace text;
begin
    select n.nspname into op_namespace
    from pg_opclass o
    join pg_namespace n on n.oid = o.opcnamespace
    where o.opcname = 'gin_trgm_ops'
    limit 1;

    if op_namespace is null then
        raise exception 'Falta la extensión pg_trgm.';
    end if;

    execute format(
        'create index if not exists wallpapers_search_text_trgm_idx '
        'on public.wallpapers using gin (search_text %I.gin_trgm_ops)',
        op_namespace
    );
end $$;

-- El feed ordena por fecha y id; los índices parciales dejan fuera la
-- papelera y los archivados.
create index if not exists wallpapers_feed_idx
    on public.wallpapers (created_at desc, id desc)
    where deleted_at is null and archived_at is null;

create index if not exists wallpapers_featured_feed_idx
    on public.wallpapers (created_at desc, id desc)
    where featured and deleted_at is null and archived_at is null;

create index if not exists wallpapers_category_feed_idx
    on public.wallpapers (category, created_at desc, id desc)
    where deleted_at is null and archived_at is null;

create index if not exists collection_items_order_idx
    on public.collection_items (collection_id, position, created_at, wallpaper_id);

create index if not exists collections_created_at_idx
    on public.collections (created_at desc);

-- Cada etiqueta queda como una palabra corta, sin hash ni espacios de más.
create or replace function public.wallpaper_tags_valid(p_tags text[])
returns boolean
language sql
immutable
set search_path = public, pg_temp
as $$
    select coalesce(
        bool_and(
            candidate.tag = btrim(candidate.tag)
            and char_length(candidate.tag) between 1 and 40
            and candidate.tag !~ '\s+#'
        ),
        true
    )
    from unnest(coalesce(p_tags, '{}'::text[])) as candidate(tag);
$$;

revoke all on function public.wallpaper_tags_valid(text[]) from public;
grant execute on function public.wallpaper_tags_valid(text[]) to anon, authenticated;

alter table public.wallpapers
    drop constraint if exists wallpapers_tags_shape;
alter table public.wallpapers
    add constraint wallpapers_tags_shape
    check (public.wallpaper_tags_valid(tags)) not valid;
alter table public.wallpapers
    validate constraint wallpapers_tags_shape;

alter table public.wallpapers
    drop constraint if exists wallpapers_downloads_nonneg;
alter table public.wallpapers
    add constraint wallpapers_downloads_nonneg
    check (downloads >= 0);

alter table public.wallpapers
    drop constraint if exists wallpapers_size_nonneg;
alter table public.wallpapers
    add constraint wallpapers_size_nonneg
    check (width >= 0 and height >= 0);

alter table public.wallpapers
    drop constraint if exists wallpapers_featured_position_nonneg;
alter table public.wallpapers
    add constraint wallpapers_featured_position_nonneg
    check (featured_position >= 0);

alter table public.categories
    drop constraint if exists categories_name_shape;
alter table public.categories
    add constraint categories_name_shape
    check (char_length(btrim(name)) between 1 and 40);

alter table public.tag_categories
    drop constraint if exists tag_categories_tag_shape;
alter table public.tag_categories
    add constraint tag_categories_tag_shape
    check (tag = lower(btrim(tag)) and char_length(tag) between 1 and 40);

alter table public.tag_categories
    drop constraint if exists tag_categories_display_shape;
alter table public.tag_categories
    add constraint tag_categories_display_shape
    check (char_length(btrim(display_tag)) between 1 and 40);

alter table public.collections
    drop constraint if exists collections_name_shape;
alter table public.collections
    add constraint collections_name_shape
    check (char_length(btrim(name)) between 1 and 80);

alter table public.collections
    drop constraint if exists collections_description_len;
alter table public.collections
    add constraint collections_description_len
    check (char_length(description) <= 2000);

alter table public.collection_items
    drop constraint if exists collection_items_position_nonneg;
alter table public.collection_items
    add constraint collection_items_position_nonneg
    check (position >= 0);

-- Las funciones de trigger no se llaman desde la API.
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create or replace function public.touch_category()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create or replace function public.sync_wallpaper_category()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    if new.category_id is not null then
        select name into new.category
        from public.categories
        where id = new.category_id;
    end if;
    return new;
end;
$$;

create or replace function public.rename_category_text()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    if new.name is distinct from old.name then
        update public.wallpapers
        set category = new.name
        where category_id = new.id;
    end if;
    return new;
end;
$$;

revoke all on function public.touch_updated_at() from public;
revoke all on function public.touch_category() from public;
revoke all on function public.sync_wallpaper_category() from public;
revoke all on function public.rename_category_text() from public;
revoke all on function public.normalize_wallpaper_tags_trigger() from public;

analyze public.wallpapers;
analyze public.categories;
analyze public.tag_categories;
analyze public.collections;
analyze public.collection_items;

notify pgrst, 'reload schema';
