-- Deja las etiquetas de los fondos como valores sueltos y sin distinguir
-- mayúsculas al filtrar. Es idempotente.
--
-- Los fondos viejos pueden guardar varios hashtags en un mismo elemento
-- ("#playa #sol"). Eso hacía que el filtro por etiqueta no los encontrara.

-- Etiquetas ya separadas, sin repetidos y hasta 12, conservando el orden.
create or replace function public.normalize_wallpaper_tags(p_tags text[])
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
    from (
        select label, tag_position, part_position
        from first_seen
        order by tag_position, part_position
        limit 12
    ) picked;
$$;

revoke all on function public.normalize_wallpaper_tags(text[]) from public;
grant execute on function public.normalize_wallpaper_tags(text[]) to authenticated;

-- Copia en minúsculas para que el filtro no dependa de cómo se escribió.
create or replace function public.lower_wallpaper_tags(p_tags text[])
returns text[]
language sql
immutable
strict
set search_path = public, pg_temp
as $$
    select coalesce(
        array_agg(lower(candidate.tag) order by candidate.position),
        '{}'::text[]
    )
    from unnest(p_tags) with ordinality as candidate(tag, position);
$$;

revoke all on function public.lower_wallpaper_tags(text[]) from public;
grant execute on function public.lower_wallpaper_tags(text[]) to authenticated;

alter table public.wallpapers
    add column if not exists tags_lower text[]
    generated always as (public.lower_wallpaper_tags(tags)) stored;

create index if not exists wallpapers_tags_lower_idx
    on public.wallpapers using gin (tags_lower);

-- Los valores compuestos que ya estaban guardados pasan a etiquetas sueltas.
update public.wallpapers
set tags = public.normalize_wallpaper_tags(tags)
where coalesce(tags, '{}'::text[]) <> public.normalize_wallpaper_tags(tags);

-- Lo que se guarde de ahora en adelante queda igual de prolijo.
create or replace function public.normalize_wallpaper_tags_trigger()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    new.tags := public.normalize_wallpaper_tags(new.tags);
    return new;
end;
$$;

drop trigger if exists wallpapers_normalize_tags on public.wallpapers;
create trigger wallpapers_normalize_tags
    before insert or update of tags on public.wallpapers
    for each row
    execute function public.normalize_wallpaper_tags_trigger();
