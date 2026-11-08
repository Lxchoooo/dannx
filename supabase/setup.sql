-- Documento histórico del esquema base. NO se aplica automáticamente.
-- La fuente de verdad son los archivos de docs/migraciones/.
-- En la base viva las políticas de colecciones se llaman *_read_visible
-- y la columna de privacidad es is_public (ver 20261109_fix_collections.sql).

-- DannX backend setup. Idempotent: safe to run again after updates.
--
-- Setup checklist:
--   1. Create a free project at supabase.com and open the SQL Editor.
--   2. This file already carries the admin UUID:
--      87645d98-691d-42a7-b1f3-b18f05cabd59
--      If you ever recreate the admin account, paste the SQL again with the
--      new UUID. Until then, every other write is rejected.
--   3. Paste this file and run it.
--   4. Authentication > Providers > Email: turn off "Allow new users to sign up".
--   5. Storage > wallpapers: this file sets the 25 MB limit and the allowed
--      image types. Do not remove them in the dashboard.
--   6. Project Settings > API: copy the Project URL and anon public key into the
--      repository secrets SUPABASE_URL and SUPABASE_ANON_KEY.
--
-- Write access is limited to the admin UUID below. Read access stays public.

create extension if not exists "pgcrypto";

create table if not exists public.wallpapers (
    id uuid primary key default gen_random_uuid(),
    title text not null,
    category text not null default 'Abstracto',
    storage_path text not null unique,
    author text not null default 'DannX',
    width int not null default 0,
    height int not null default 0,
    featured boolean not null default false,
    downloads int not null default 0,
    created_at timestamptz not null default now()
);

alter table public.wallpapers
    add column if not exists tags text[] not null default '{}';

-- Auditoría: quién creó la fila y cuándo fue la última modificación.
alter table public.wallpapers
    add column if not exists updated_at timestamptz not null default now();

alter table public.wallpapers
    add column if not exists created_by uuid references auth.users(id);

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists wallpapers_touch_updated_at on public.wallpapers;
create trigger wallpapers_touch_updated_at
    before update on public.wallpapers
    for each row
    execute function public.touch_updated_at();

-- Límites de contenido para que la API no acepte texto ni tags arbitrarios.
alter table public.wallpapers
    drop constraint if exists wallpapers_title_len;
alter table public.wallpapers
    add constraint wallpapers_title_len
    check (char_length(title) between 1 and 120);

alter table public.wallpapers
    drop constraint if exists wallpapers_category_len;
alter table public.wallpapers
    add constraint wallpapers_category_len
    check (char_length(category) <= 40);

alter table public.wallpapers
    drop constraint if exists wallpapers_author_len;
alter table public.wallpapers
    add constraint wallpapers_author_len
    check (char_length(author) <= 60);

alter table public.wallpapers
    drop constraint if exists wallpapers_tags_count;
alter table public.wallpapers
    add constraint wallpapers_tags_count
    check (coalesce(array_length(tags, 1), 0) <= 12);

alter table public.wallpapers enable row level security;

drop policy if exists "wallpapers_public_read" on public.wallpapers;
create policy "wallpapers_public_read"
    on public.wallpapers
    for select
    using (true);

drop policy if exists "wallpapers_admin_insert" on public.wallpapers;
create policy "wallpapers_admin_insert"
    on public.wallpapers
    for insert
    to authenticated
    with check (auth.uid() = '87645d98-691d-42a7-b1f3-b18f05cabd59'::uuid);

drop policy if exists "wallpapers_admin_update" on public.wallpapers;
create policy "wallpapers_admin_update"
    on public.wallpapers
    for update
    to authenticated
    using (auth.uid() = '87645d98-691d-42a7-b1f3-b18f05cabd59'::uuid)
    with check (auth.uid() = '87645d98-691d-42a7-b1f3-b18f05cabd59'::uuid);

drop policy if exists "wallpapers_admin_delete" on public.wallpapers;
create policy "wallpapers_admin_delete"
    on public.wallpapers
    for delete
    to authenticated
    using (auth.uid() = '87645d98-691d-42a7-b1f3-b18f05cabd59'::uuid);

-- Bucket público de solo lectura para el catálogo. Las escrituras quedan
-- limitadas al admin y el propio bucket impone tamaño y tipo de archivo.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'wallpapers',
    'wallpapers',
    true,
    26214400,
    array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update set
    public = true,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "wallpapers_storage_read" on storage.objects;
create policy "wallpapers_storage_read"
    on storage.objects
    for select
    using (bucket_id = 'wallpapers');

drop policy if exists "wallpapers_storage_insert" on storage.objects;
create policy "wallpapers_storage_insert"
    on storage.objects
    for insert
    to authenticated
    with check (
        bucket_id = 'wallpapers'
        and auth.uid() = '87645d98-691d-42a7-b1f3-b18f05cabd59'::uuid
    );

drop policy if exists "wallpapers_storage_update" on storage.objects;
create policy "wallpapers_storage_update"
    on storage.objects
    for update
    to authenticated
    using (
        bucket_id = 'wallpapers'
        and auth.uid() = '87645d98-691d-42a7-b1f3-b18f05cabd59'::uuid
    )
    with check (
        bucket_id = 'wallpapers'
        and auth.uid() = '87645d98-691d-42a7-b1f3-b18f05cabd59'::uuid
    );

drop policy if exists "wallpapers_storage_delete" on storage.objects;
create policy "wallpapers_storage_delete"
    on storage.objects
    for delete
    to authenticated
    using (
        bucket_id = 'wallpapers'
        and auth.uid() = '87645d98-691d-42a7-b1f3-b18f05cabd59'::uuid
    );

create table if not exists public.collections (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    owner_name text not null default 'DannX',
    name text not null,
    description text not null default '',
    is_public boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.collection_items (
    collection_id uuid not null references public.collections(id) on delete cascade,
    wallpaper_id uuid not null references public.wallpapers(id) on delete cascade,
    position int not null default 0,
    created_at timestamptz not null default now(),
    primary key (collection_id, wallpaper_id)
);

create index if not exists collections_owner_idx
    on public.collections(owner_id);

create index if not exists collection_items_wallpaper_idx
    on public.collection_items(wallpaper_id);

-- Índices para las consultas que hace la app: orden por fecha y filtro por tags.
create index if not exists wallpapers_created_at_idx
    on public.wallpapers(created_at desc);

create index if not exists wallpapers_tags_idx
    on public.wallpapers using gin (tags);

alter table public.collections enable row level security;
alter table public.collection_items enable row level security;

drop policy if exists "collections_public_read" on public.collections;
create policy "collections_public_read"
    on public.collections
    for select
    using (is_public or owner_id = auth.uid());

drop policy if exists "collections_owner_insert" on public.collections;
create policy "collections_owner_insert"
    on public.collections
    for insert
    to authenticated
    with check (owner_id = auth.uid());

drop policy if exists "collections_owner_update" on public.collections;
create policy "collections_owner_update"
    on public.collections
    for update
    to authenticated
    using (owner_id = auth.uid())
    with check (owner_id = auth.uid());

drop policy if exists "collections_owner_delete" on public.collections;
create policy "collections_owner_delete"
    on public.collections
    for delete
    to authenticated
    using (owner_id = auth.uid());

drop policy if exists "collection_items_public_read" on public.collection_items;
create policy "collection_items_public_read"
    on public.collection_items
    for select
    using (
        exists (
            select 1
            from public.collections c
            where c.id = collection_id
              and (c.is_public or c.owner_id = auth.uid())
        )
    );

drop policy if exists "collection_items_owner_insert" on public.collection_items;
create policy "collection_items_owner_insert"
    on public.collection_items
    for insert
    to authenticated
    with check (
        exists (
            select 1
            from public.collections c
            where c.id = collection_id
              and c.owner_id = auth.uid()
        )
    );

drop policy if exists "collection_items_owner_update" on public.collection_items;
create policy "collection_items_owner_update"
    on public.collection_items
    for update
    to authenticated
    using (
        exists (
            select 1
            from public.collections c
            where c.id = collection_id
              and c.owner_id = auth.uid()
        )
    )
    with check (
        exists (
            select 1
            from public.collections c
            where c.id = collection_id
              and c.owner_id = auth.uid()
        )
    );

drop policy if exists "collection_items_owner_delete" on public.collection_items;
create policy "collection_items_owner_delete"
    on public.collection_items
    for delete
    to authenticated
    using (
        exists (
            select 1
            from public.collections c
            where c.id = collection_id
              and c.owner_id = auth.uid()
        )
    );

grant select on public.wallpapers to anon, authenticated;
grant insert, update, delete on public.wallpapers to authenticated;
grant select on public.collections to anon, authenticated;
grant insert, update, delete on public.collections to authenticated;
grant select on public.collection_items to anon, authenticated;
grant insert, update, delete on public.collection_items to authenticated;

notify pgrst, 'reload schema';

-- El contador de descargas se retira: cualquiera con la clave anon podía
-- inflarlo sin límite y no aportaba una métrica confiable.
drop function if exists public.increment_downloads(uuid);
