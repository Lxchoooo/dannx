-- Unifica la escritura de las etiquetas: "Cleopatra" y "cleopatra" son la
-- misma etiqueta y se guardan con una sola forma. Es idempotente.

-- Etiqueta canónica por clave en minúsculas. La app no la consulta: la usan
-- las funciones internas al guardar.
create table if not exists public.tag_labels (
    tag text primary key,
    label text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint tag_labels_tag_shape
        check (tag = lower(btrim(tag)) and btrim(tag) <> ''),
    constraint tag_labels_label_shape
        check (label = btrim(label) and btrim(label) <> '')
);

alter table public.tag_labels enable row level security;

-- La app no lee esta tabla: solo la usan las funciones internas.
revoke all on public.tag_labels from public, anon, authenticated;

drop trigger if exists tag_labels_touch_updated_at on public.tag_labels;
create trigger tag_labels_touch_updated_at
    before update on public.tag_labels
    for each row
    execute function public.touch_category();

-- Una regla guardada manda sobre la escritura que se haya usado en los fondos.
insert into public.tag_labels (tag, label)
select tc.tag, btrim(tc.display_tag)
from public.tag_categories tc
where btrim(tc.display_tag) <> ''
on conflict (tag) do update
    set label = excluded.label,
        updated_at = now();

-- Para el resto, gana la escritura más usada; si hay empate, la más reciente.
with usage as (
    select
        lower(label.value) as tag,
        label.value as label,
        count(*) as uses,
        max(w.created_at) as last_seen
    from public.wallpapers as w
    cross join lateral unnest(public.wallpaper_tag_labels(w.tags)) as label(value)
    group by lower(label.value), label.value
),
ranked as (
    select
        tag,
        label,
        row_number() over (
            partition by tag
            order by uses desc, last_seen desc, label asc
        ) as position
    from usage
)
insert into public.tag_labels (tag, label)
select tag, label
from ranked
where position = 1
on conflict (tag) do nothing;

-- Los fondos existentes pasan a la escritura canónica.
update public.wallpapers as w
set tags = (
    select coalesce(
        array_agg(coalesce(l.label, candidate.tag) order by candidate.position),
        '{}'::text[]
    )
    from unnest(w.tags) with ordinality as candidate(tag, position)
    left join public.tag_labels as l
        on l.tag = lower(btrim(candidate.tag))
)
where exists (
    select 1
    from unnest(w.tags) with ordinality as candidate(tag, position)
    join public.tag_labels as l
        on l.tag = lower(btrim(candidate.tag))
    where l.label is distinct from candidate.tag
);

-- Al guardar un fondo, cada etiqueta toma la escritura canónica.
create or replace function public.canonicalize_wallpaper_tags(p_tags text[])
returns text[]
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    clean text[] := public.normalize_wallpaper_tags(p_tags);
begin
    if coalesce(array_length(clean, 1), 0) = 0 then
        return '{}'::text[];
    end if;

    insert into public.tag_labels (tag, label)
    select lower(candidate.tag), candidate.tag
    from unnest(clean) as candidate(tag)
    on conflict (tag) do nothing;

    return (
        select coalesce(
            array_agg(coalesce(label.label, candidate.tag) order by candidate.position),
            '{}'::text[]
        )
        from unnest(clean) with ordinality as candidate(tag, position)
        left join public.tag_labels as label
            on label.tag = lower(candidate.tag)
    );
end;
$$;

revoke all on function public.canonicalize_wallpaper_tags(text[]) from public;

create or replace function public.normalize_wallpaper_tags_trigger()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    new.tags := public.canonicalize_wallpaper_tags(new.tags);
    return new;
end;
$$;

revoke all on function public.normalize_wallpaper_tags_trigger() from public;

-- Cambiar el texto de una regla cambia también la escritura canónica.
create or replace function public.sync_tag_label()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    insert into public.tag_labels (tag, label)
    values (new.tag, btrim(new.display_tag))
    on conflict (tag) do update
        set label = excluded.label,
            updated_at = now();
    return new;
end;
$$;

revoke all on function public.sync_tag_label() from public;

drop trigger if exists tag_categories_sync_label on public.tag_categories;
create trigger tag_categories_sync_label
    after insert or update of display_tag on public.tag_categories
    for each row
    execute function public.sync_tag_label();

-- Renombrar una etiqueta actualiza primero la escritura canónica: así el
-- cambio de mayúsculas no se pierde al volver a guardar los fondos.
create or replace function public.rename_wallpaper_tag(
    p_old_tag text,
    p_new_tag text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_label text := left(btrim(coalesce(p_new_tag, '')), 40);
    v_new text := lower(v_label);
    v_old text := lower(left(btrim(coalesce(p_old_tag, '')), 40));
begin
    if not public.is_admin() then
        raise exception 'Solo administradores pueden editar etiquetas.'
            using errcode = '42501';
    end if;

    if v_old = '' or v_new = '' then
        return;
    end if;

    if not exists (
        select 1
        from public.wallpapers as w
        where exists (
            select 1
            from unnest(coalesce(w.tags, '{}'::text[])) as existing(tag)
            where lower(btrim(existing.tag)) = v_old
        )
    ) then
        return;
    end if;

    insert into public.tag_labels (tag, label)
    values (v_new, v_label)
    on conflict (tag) do update
        set label = excluded.label,
            updated_at = now();

    if v_old <> v_new then
        delete from public.tag_labels where tag = v_old;
    end if;

    -- La regla de esa etiqueta muestra la escritura nueva.
    update public.tag_categories
    set display_tag = v_label
    where tag = v_new
      and display_tag is distinct from v_label;

    update public.wallpapers as w
    set tags = public.normalize_wallpaper_tags(
        array(
            select case
                when lower(btrim(candidate.tag)) = v_old then v_label
                else btrim(candidate.tag)
            end
            from unnest(coalesce(w.tags, '{}'::text[]))
                with ordinality as candidate(tag, position)
            order by candidate.position
        )
    )
    where exists (
        select 1
        from unnest(coalesce(w.tags, '{}'::text[])) as existing(tag)
        where lower(btrim(existing.tag)) = v_old
    );

    -- Si la escritura nueva ya existía con otra forma, esos fondos también
    -- toman el texto elegido para que no queden dos variantes.
    update public.wallpapers as w
    set tags = public.normalize_wallpaper_tags(
        array(
            select case
                when lower(btrim(candidate.tag)) = v_new then v_label
                else btrim(candidate.tag)
            end
            from unnest(coalesce(w.tags, '{}'::text[]))
                with ordinality as candidate(tag, position)
            order by candidate.position
        )
    )
    where exists (
        select 1
        from unnest(coalesce(w.tags, '{}'::text[])) as existing(tag)
        where lower(btrim(existing.tag)) = v_new
          and btrim(existing.tag) <> v_label
    );
end;
$$;

revoke all on function public.rename_wallpaper_tag(text, text) from public;
grant execute on function public.rename_wallpaper_tag(text, text) to authenticated;

notify pgrst, 'reload schema';
