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
