-- Panel del Estudio: categorías, archivo y administración.
-- Es idempotente. Ejecutalo completo en el editor SQL del panel.
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
do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'wallpapers'
          and column_name = 'created_by'
    ) then
        insert into public.admins (user_id)
        select distinct (regexp_match(with_check, '([0-9a-fA-F-]{36})'))[1]::uuid
        from pg_policies
        where schemaname = 'public'
          and tablename = 'wallpapers'
          and policyname in ('wallpapers_admin_insert', 'wallpapers_admin_update')
          and with_check ~ '[0-9a-fA-F-]{36}'
        on conflict (user_id) do nothing;
    end if;

    insert into public.admins (user_id)
    select distinct (regexp_match(with_check, '([0-9a-fA-F-]{36})'))[1]::uuid
    from pg_policies
    where schemaname = 'storage'
      and tablename = 'objects'
      and policyname in (
          'wallpapers_storage_insert',
          'wallpapers_storage_update',
          'wallpapers_storage_delete'
      )
      and with_check ~ '[0-9a-fA-F-]{36}'
    on conflict (user_id) do nothing;
end;
$$;

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

notify pgrst, 'reload schema';

do $$
begin
    if not exists (select 1 from public.admins) then
        raise notice
            'No hay administradores. Agregá tu usuario en public.admins antes de usar el Estudio.';
    end if;
end;
$$;
