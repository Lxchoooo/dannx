-- Panel del Estudio: categorías, archivo, etiquetas, colecciones y
-- administración. Es idempotente. Ejecutalo completo en el editor SQL del
-- panel.
--
-- Antes de terminar, revisá el aviso final: si public.admins quedó vacío,
-- agregá tu usuario administrador desde el editor SQL.

create extension if not exists "pgcrypto";

-- Lista de administradores. La app no lee esta tabla: solo la usa una función
-- interna para decidir quién puede escribir.
create table if not exists public.admins (
    user_id uuid primary key references auth.users(id) on delete cascade,
    created_at timestamptz not null default now()
);

alter table public.admins enable row level security;

-- Si las políticas viejas tenían el UUID del administrador, lo recuperamos de
-- ellas antes de reemplazarlas. Así no hay que copiar IDs a este archivo.
insert into public.admins (user_id)
select distinct candidate.user_id
from (
    select (regexp_match(with_check, '([0-9a-fA-F-]{36})'))[1]::uuid as user_id
    from pg_policies
    where schemaname = 'public'
      and tablename = 'wallpapers'
      and policyname in ('wallpapers_admin_insert', 'wallpapers_admin_update')
      and with_check ~ '[0-9a-fA-F-]{36}'

    union all

    select (regexp_match(with_check, '([0-9a-fA-F-]{36})'))[1]::uuid as user_id
    from pg_policies
    where schemaname = 'storage'
      and tablename = 'objects'
      and policyname in (
          'wallpapers_storage_insert',
          'wallpapers_storage_update',
          'wallpapers_storage_delete'
      )
      and with_check ~ '[0-9a-fA-F-]{36}'
) candidate
join auth.users u on u.id = candidate.user_id
on conflict (user_id) do nothing;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select exists (
        select 1
        from public.admins
        where user_id = auth.uid()
    );
$$;

revoke all on function public.is_admin() from public;
grant execute on function public.is_admin() to anon, authenticated;

-- Categorías reales. El texto de wallpapers.category se mantiene sincronizado
-- para que las versiones anteriores de la app sigan leyendo bien.
create table if not exists public.categories (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    position int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists categories_name_lower_idx
    on public.categories (lower(name));

create index if not exists categories_position_idx
    on public.categories (position asc, name asc);

alter table public.categories enable row level security;

drop policy if exists "categories_public_read" on public.categories;
create policy "categories_public_read"
    on public.categories
    for select
    using (true);

drop policy if exists "categories_admin_insert" on public.categories;
create policy "categories_admin_insert"
    on public.categories
    for insert
    to authenticated
    with check (public.is_admin());

drop policy if exists "categories_admin_update" on public.categories;
create policy "categories_admin_update"
    on public.categories
    for update
    to authenticated
    using (public.is_admin())
    with check (public.is_admin());

drop policy if exists "categories_admin_delete" on public.categories;
create policy "categories_admin_delete"
    on public.categories
    for delete
    to authenticated
    using (public.is_admin());

grant select on public.categories to anon, authenticated;
grant insert, update, delete on public.categories to authenticated;

alter table public.wallpapers
    add column if not exists category_id uuid references public.categories(id)
        on delete set null;

alter table public.wallpapers
    add column if not exists archived_at timestamptz;

alter table public.wallpapers
    alter column created_by set default auth.uid();

create index if not exists wallpapers_category_idx
    on public.wallpapers(category_id);

create index if not exists wallpapers_archived_idx
    on public.wallpapers(archived_at);

-- Las categorías que ya existían como texto pasan a la tabla nueva.
insert into public.categories (name)
select candidate.name
from (
    select distinct on (lower(btrim(category)))
        btrim(category) as name
    from public.wallpapers
    where btrim(category) <> ''
    order by lower(btrim(category)), btrim(category)
) candidate
where not exists (
    select 1
    from public.categories c
    where lower(c.name) = lower(candidate.name)
);

update public.wallpapers w
set category_id = c.id
from public.categories c
where w.category_id is null
  and lower(btrim(w.category)) = lower(c.name);

create or replace function public.sync_wallpaper_category()
returns trigger
language plpgsql
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

drop trigger if exists wallpapers_sync_category on public.wallpapers;
create trigger wallpapers_sync_category
    before insert or update of category_id on public.wallpapers
    for each row
    execute function public.sync_wallpaper_category();

create or replace function public.touch_category()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists categories_touch_updated_at on public.categories;
create trigger categories_touch_updated_at
    before update on public.categories
    for each row
    execute function public.touch_category();

create or replace function public.rename_category_text()
returns trigger
language plpgsql
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

drop trigger if exists categories_rename_wallpapers on public.categories;
create trigger categories_rename_wallpapers
    after update of name on public.categories
    for each row
    execute function public.rename_category_text();

-- El feed público deja de mostrar los fondos archivados. El panel autenticado
-- sigue viéndolos gracias a is_admin().
drop policy if exists "wallpapers_public_read" on public.wallpapers;
create policy "wallpapers_public_read"
    on public.wallpapers
    for select
    using (archived_at is null or public.is_admin());

drop policy if exists "wallpapers_admin_insert" on public.wallpapers;
create policy "wallpapers_admin_insert"
    on public.wallpapers
    for insert
    to authenticated
    with check (public.is_admin());

drop policy if exists "wallpapers_admin_update" on public.wallpapers;
create policy "wallpapers_admin_update"
    on public.wallpapers
    for update
    to authenticated
    using (public.is_admin())
    with check (public.is_admin());

drop policy if exists "wallpapers_admin_delete" on public.wallpapers;
create policy "wallpapers_admin_delete"
    on public.wallpapers
    for delete
    to authenticated
    using (public.is_admin());

-- Las imágenes del bucket quedan limitadas al administrador.
drop policy if exists "wallpapers_storage_insert" on storage.objects;
create policy "wallpapers_storage_insert"
    on storage.objects
    for insert
    to authenticated
    with check (
        bucket_id = 'wallpapers'
        and public.is_admin()
    );

drop policy if exists "wallpapers_storage_update" on storage.objects;
create policy "wallpapers_storage_update"
    on storage.objects
    for update
    to authenticated
    using (
        bucket_id = 'wallpapers'
        and public.is_admin()
    )
    with check (
        bucket_id = 'wallpapers'
        and public.is_admin()
    );

drop policy if exists "wallpapers_storage_delete" on storage.objects;
create policy "wallpapers_storage_delete"
    on storage.objects
    for delete
    to authenticated
    using (
        bucket_id = 'wallpapers'
        and public.is_admin()
    );

grant select on public.wallpapers to anon, authenticated;
grant insert, update, delete on public.wallpapers to authenticated;
grant select on public.collections to anon, authenticated;
grant insert, update, delete on public.collections to authenticated;
grant select on public.collection_items to anon, authenticated;
grant insert, update, delete on public.collection_items to authenticated;

-- Normaliza una lista de etiquetas igual que la app: recorta espacios, quita
-- vacías, une sin distinguir mayúsculas y conserva el orden de la primera
-- aparición. El límite de 12 es el mismo que aplica el cliente.
create or replace function public.normalize_wallpaper_tags(p_tags text[])
returns text[]
language sql
immutable
set search_path = public, pg_temp
as $$
    select coalesce(array_agg(clean_tag order by first_position), '{}'::text[])
    from (
        select
            (array_agg(btrim(candidate.tag) order by candidate.position))[1] as clean_tag,
            min(candidate.position) as first_position
        from unnest(coalesce(p_tags, '{}'::text[]))
            with ordinality as candidate(tag, position)
        where btrim(candidate.tag) <> ''
        group by lower(btrim(candidate.tag))
        order by first_position
        limit 12
    ) normalized;
$$;

-- Agrega una etiqueta a todos los fondos indicados en una sola sentencia.
create or replace function public.add_wallpaper_tag(
    p_tag text,
    p_wallpaper_ids uuid[]
)
returns void
language sql
security invoker
set search_path = public, pg_temp
as $$
    update public.wallpapers as w
    set tags = public.normalize_wallpaper_tags(
        coalesce(w.tags, '{}'::text[]) || btrim(p_tag)
    )
    where w.id = any(p_wallpaper_ids)
      and public.is_admin()
      and btrim(p_tag) <> ''
      and not exists (
          select 1
          from unnest(coalesce(w.tags, '{}'::text[])) as existing(tag)
          where lower(btrim(existing.tag)) = lower(btrim(p_tag))
      );
$$;

-- Renombra una etiqueta en todos los fondos que la tengan.
create or replace function public.rename_wallpaper_tag(
    p_old_tag text,
    p_new_tag text
)
returns void
language sql
security invoker
set search_path = public, pg_temp
as $$
    update public.wallpapers as w
    set tags = public.normalize_wallpaper_tags(
        array(
            select case
                when lower(btrim(candidate.tag)) = lower(btrim(p_old_tag))
                    then btrim(p_new_tag)
                else btrim(candidate.tag)
            end
            from unnest(coalesce(w.tags, '{}'::text[]))
                with ordinality as candidate(tag, position)
            order by candidate.position
        )
    )
    where public.is_admin()
      and btrim(p_old_tag) <> ''
      and btrim(p_new_tag) <> ''
      and exists (
          select 1
          from unnest(coalesce(w.tags, '{}'::text[])) as existing(tag)
          where lower(btrim(existing.tag)) = lower(btrim(p_old_tag))
      );
$$;

-- Quita una etiqueta de todos los fondos que la tengan.
create or replace function public.delete_wallpaper_tag(p_tag text)
returns void
language sql
security invoker
set search_path = public, pg_temp
as $$
    update public.wallpapers as w
    set tags = public.normalize_wallpaper_tags(
        array(
            select candidate.tag
            from unnest(coalesce(w.tags, '{}'::text[]))
                with ordinality as candidate(tag, position)
            where lower(btrim(candidate.tag)) <> lower(btrim(p_tag))
            order by candidate.position
        )
    )
    where public.is_admin()
      and btrim(p_tag) <> ''
      and exists (
          select 1
          from unnest(coalesce(w.tags, '{}'::text[])) as existing(tag)
          where lower(btrim(existing.tag)) = lower(btrim(p_tag))
      );
$$;

-- Reordena todos los elementos de una colección en una sola sentencia.
create or replace function public.reorder_collection(
    p_collection_id uuid,
    p_wallpaper_ids uuid[]
)
returns void
language sql
security invoker
set search_path = public, pg_temp
as $$
    with ordered as (
        select
            candidate.wallpaper_id,
            (min(candidate.position) - 1)::int as position
        from unnest(p_wallpaper_ids)
            with ordinality as candidate(wallpaper_id, position)
        group by candidate.wallpaper_id
    )
    update public.collection_items as item
    set position = ordered.position
    from ordered
    where item.collection_id = p_collection_id
      and item.wallpaper_id = ordered.wallpaper_id
      and public.is_admin();
$$;

revoke all on function public.normalize_wallpaper_tags(text[]) from public;
grant execute on function public.normalize_wallpaper_tags(text[]) to authenticated;

revoke all on function public.add_wallpaper_tag(text, uuid[]) from public;
grant execute on function public.add_wallpaper_tag(text, uuid[]) to authenticated;

revoke all on function public.rename_wallpaper_tag(text, text) from public;
grant execute on function public.rename_wallpaper_tag(text, text) to authenticated;

revoke all on function public.delete_wallpaper_tag(text) from public;
grant execute on function public.delete_wallpaper_tag(text) to authenticated;

revoke all on function public.reorder_collection(uuid, uuid[]) from public;
grant execute on function public.reorder_collection(uuid, uuid[]) to authenticated;

notify pgrst, 'reload schema';

do $$
begin
    if not exists (select 1 from public.admins) then
        raise notice
            'No hay administradores. Agregá tu usuario en public.admins antes de usar el Estudio.';
    end if;
end;
$$;
