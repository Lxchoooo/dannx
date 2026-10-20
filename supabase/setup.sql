-- DannX backend setup. Run once in the Supabase SQL Editor.

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
    with check (true);

drop policy if exists "wallpapers_admin_update" on public.wallpapers;
create policy "wallpapers_admin_update"
    on public.wallpapers
    for update
    to authenticated
    using (true)
    with check (true);

drop policy if exists "wallpapers_admin_delete" on public.wallpapers;
create policy "wallpapers_admin_delete"
    on public.wallpapers
    for delete
    to authenticated
    using (true);

insert into storage.buckets (id, name, public)
values ('wallpapers', 'wallpapers', true)
on conflict (id) do update set public = true;

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
    with check (bucket_id = 'wallpapers');

drop policy if exists "wallpapers_storage_update" on storage.objects;
create policy "wallpapers_storage_update"
    on storage.objects
    for update
    to authenticated
    using (bucket_id = 'wallpapers')
    with check (bucket_id = 'wallpapers');

drop policy if exists "wallpapers_storage_delete" on storage.objects;
create policy "wallpapers_storage_delete"
    on storage.objects
    for delete
    to authenticated
    using (bucket_id = 'wallpapers');

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

grant select on public.collections to anon, authenticated;
grant insert, update, delete on public.collections to authenticated;
grant select on public.collection_items to anon, authenticated;
grant insert, delete on public.collection_items to authenticated;

notify pgrst, 'reload schema';

create or replace function public.increment_downloads(target uuid)
returns void
language sql
security definer
set search_path = public
as $$
    update public.wallpapers
    set downloads = downloads + 1
    where id = target;
$$;

revoke all on function public.increment_downloads(uuid) from public;
grant execute on function public.increment_downloads(uuid) to anon, authenticated;
