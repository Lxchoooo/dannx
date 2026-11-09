-- Contenido editable de la portada y orden manual de destacados.
-- Es idempotente: se puede ejecutar completo en el editor SQL de Supabase.

create extension if not exists "pgcrypto";

-- Textos globales de la app. Son filas clave/valor para poder sumar campos
-- nuevos sin volver a cambiar el esquema.
create table if not exists public.app_settings (
    key text primary key,
    value text not null default '',
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id)
);

alter table public.app_settings enable row level security;

drop policy if exists "app_settings_public_read" on public.app_settings;
create policy "app_settings_public_read"
    on public.app_settings
    for select
    using (true);

drop policy if exists "app_settings_admin_insert" on public.app_settings;
create policy "app_settings_admin_insert"
    on public.app_settings
    for insert
    to authenticated
    with check (public.is_admin());

drop policy if exists "app_settings_admin_update" on public.app_settings;
create policy "app_settings_admin_update"
    on public.app_settings
    for update
    to authenticated
    using (public.is_admin())
    with check (public.is_admin());

grant select on public.app_settings to anon, authenticated;
grant insert, update on public.app_settings to authenticated;

insert into public.app_settings (key, value)
values
    ('home_tagline', ''),
    ('home_banner_text', ''),
    ('home_banner_enabled', 'false')
on conflict (key) do nothing;

-- Posición manual dentro del carrusel de destacados. El feed sigue ordenado
-- por fecha; la app ordena solo los destacados por este campo.
alter table public.wallpapers
    add column if not exists featured_position int not null default 0;

create index if not exists wallpapers_featured_position_idx
    on public.wallpapers (featured_position asc)
    where featured = true;
