-- Relación persistente entre una etiqueta y su categoría.
-- Es idempotente. Ejecutalo después de 20261110_studio_trash_bulk.sql.
--
-- La regla vive en el servidor para que valga en todos los dispositivos y para
-- que el panel pueda aplicarla a todos los fondos con esa etiqueta en una sola
-- operación. La categoría elegida a mano en un fondo sigue ganando: esta regla
-- solo se aplica cuando el administrador la confirma o al crear un fondo.

create table if not exists public.tag_categories (
    tag         text primary key,
    display_tag text not null,
    category_id uuid not null references public.categories(id) on delete cascade,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create index if not exists tag_categories_category_idx
    on public.tag_categories(category_id);

alter table public.tag_categories enable row level security;

drop policy if exists "tag_categories_public_read" on public.tag_categories;
create policy "tag_categories_public_read"
    on public.tag_categories
    for select
    using (true);

drop policy if exists "tag_categories_admin_insert" on public.tag_categories;
create policy "tag_categories_admin_insert"
    on public.tag_categories
    for insert
    to authenticated
    with check (public.is_admin());

drop policy if exists "tag_categories_admin_update" on public.tag_categories;
create policy "tag_categories_admin_update"
    on public.tag_categories
    for update
    to authenticated
    using (public.is_admin())
    with check (public.is_admin());

drop policy if exists "tag_categories_admin_delete" on public.tag_categories;
create policy "tag_categories_admin_delete"
    on public.tag_categories
    for delete
    to authenticated
    using (public.is_admin());

grant select on public.tag_categories to anon, authenticated;
grant insert, update, delete on public.tag_categories to authenticated;

-- Etiquetas de un fondo ya separadas. Los fondos viejos pueden guardar varios
-- hashtags en un mismo elemento ("#playa #sol"): esto devuelve "playa" y
-- "sol" para que la regla los encuentre igual. Conserva el orden original.
create or replace function public.wallpaper_tag_labels(p_tags text[])
returns text[]
language sql
immutable
set search_path = public, pg_temp
as $$
    with expanded as (
        select
            btrim(btrim(part.value), '#') as label,
            raw.position as tag_position,
            part.position as part_position
        from unnest(coalesce(p_tags, '{}'::text[]))
            with ordinality as raw(tag, position)
        cross join lateral unnest(
            regexp_split_to_array(btrim(raw.tag), '\s+#')
        ) with ordinality as part(value, position)
        where btrim(raw.tag) <> ''
    ),
    first_seen as (
        select distinct on (lower(label))
            label,
            tag_position,
            part_position
        from expanded
        where label <> ''
        order by lower(label), tag_position, part_position
    )
    select coalesce(
        array_agg(label order by tag_position, part_position),
        '{}'::text[]
    )
    from first_seen;
$$;

revoke all on function public.wallpaper_tag_labels(text[]) from public;
grant execute on function public.wallpaper_tag_labels(text[]) to anon, authenticated;

-- Guarda la regla de una o más etiquetas y recategoriza todos los fondos que
-- las tengan. Devuelve cuántos fondos quedaron con la categoría nueva.
create or replace function public.set_tag_category(
    p_tags text[],
    p_category_id uuid
)
returns integer
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
    v_name text;
    v_count integer;
begin
    if not public.is_admin() then
        raise exception 'No autorizado' using errcode = '42501';
    end if;

    select c.name into v_name
    from public.categories c
    where c.id = p_category_id;

    if v_name is null then
        raise exception 'La categoría no existe' using errcode = '22023';
    end if;

    insert into public.tag_categories (tag, display_tag, category_id)
    select lower(btrim(wanted.tag)), btrim(wanted.tag), p_category_id
    from unnest(coalesce(p_tags, '{}'::text[])) as wanted(tag)
    where btrim(wanted.tag) <> ''
    on conflict (tag) do update
        set display_tag = excluded.display_tag,
            category_id = excluded.category_id,
            updated_at = now();

    update public.wallpapers as w
    set category_id = p_category_id,
        category = v_name
    where exists (
        select 1
        from unnest(coalesce(p_tags, '{}'::text[])) as wanted(tag)
        where lower(btrim(wanted.tag)) =
            any (public.wallpaper_tag_labels(w.tags))
    );

    get diagnostics v_count = row_count;
    return v_count;
end;
$$;

revoke all on function public.set_tag_category(text[], uuid) from public;
grant execute on function public.set_tag_category(text[], uuid) to authenticated;

-- Quita la regla sin tocar los fondos: lo que ya se aplicó se queda como está.
create or replace function public.delete_tag_category(p_tag text)
returns void
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
begin
    if not public.is_admin() then
        raise exception 'No autorizado' using errcode = '42501';
    end if;

    delete from public.tag_categories
    where tag = lower(btrim(coalesce(p_tag, '')));
end;
$$;

revoke all on function public.delete_tag_category(text) from public;
grant execute on function public.delete_tag_category(text) to authenticated;
